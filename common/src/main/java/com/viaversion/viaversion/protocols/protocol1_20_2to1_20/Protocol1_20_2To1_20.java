/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaVersion
 * Copyright (C) 2023 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.viaversion.viaversion.protocols.protocol1_20_2to1_20;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.entities.Entity1_19_4Types;
import com.viaversion.viaversion.api.protocol.AbstractProtocol;
import com.viaversion.viaversion.api.protocol.packet.Direction;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.rewriter.EntityRewriter;
import com.viaversion.viaversion.api.rewriter.ItemRewriter;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.data.entity.EntityTrackerBase;
import com.viaversion.viaversion.exception.CancelException;
import com.viaversion.viaversion.protocols.base.ClientboundLoginPackets;
import com.viaversion.viaversion.protocols.base.ServerboundLoginPackets;
import com.viaversion.viaversion.protocols.protocol1_19_4to1_19_3.ClientboundPackets1_19_4;
import com.viaversion.viaversion.protocols.protocol1_19_4to1_19_3.ServerboundPackets1_19_4;
import com.viaversion.viaversion.protocols.protocol1_20_2to1_20.packet.ClientboundConfigurationPackets1_20_2;
import com.viaversion.viaversion.protocols.protocol1_20_2to1_20.packet.ClientboundPackets1_20_2;
import com.viaversion.viaversion.protocols.protocol1_20_2to1_20.packet.ServerboundConfigurationPackets1_20_2;
import com.viaversion.viaversion.protocols.protocol1_20_2to1_20.packet.ServerboundPackets1_20_2;
import com.viaversion.viaversion.protocols.protocol1_20_2to1_20.rewriter.BlockItemPacketRewriter1_20_2;
import com.viaversion.viaversion.protocols.protocol1_20_2to1_20.rewriter.EntityPacketRewriter1_20_2;
import com.viaversion.viaversion.protocols.protocol1_20_2to1_20.storage.ConfigurationState;
import com.viaversion.viaversion.rewriter.SoundRewriter;
import com.viaversion.viaversion.rewriter.StatisticsRewriter;
import com.viaversion.viaversion.rewriter.TagRewriter;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.UUID;

public final class Protocol1_20_2To1_20 extends AbstractProtocol<ClientboundPackets1_19_4, ClientboundPackets1_20_2, ServerboundPackets1_19_4, ServerboundPackets1_20_2> {

    private final EntityPacketRewriter1_20_2 entityPacketRewriter = new EntityPacketRewriter1_20_2(this);
    private final BlockItemPacketRewriter1_20_2 itemPacketRewriter = new BlockItemPacketRewriter1_20_2(this);

    public Protocol1_20_2To1_20() {
        // Passing the class types into the super constructor is needed for automatic packet type id remapping, but can otherwise be omitted
        super(ClientboundPackets1_19_4.class, ClientboundPackets1_20_2.class, ServerboundPackets1_19_4.class, ServerboundPackets1_20_2.class);
    }

    @Override
    protected void registerPackets() {
        // Close your eyes and turn around while you still can
        // TODO Handle Enabled features and tags before configuration phase end?
        // TODO Player info, replace profile with missing name with null?
        // TODO Scoreboard objective probably okay, but there are refactors to the id
        super.registerPackets();

        final TagRewriter<ClientboundPackets1_19_4> tagRewriter = new TagRewriter<>(this);
        tagRewriter.registerGeneric(ClientboundPackets1_19_4.TAGS);

        final SoundRewriter<ClientboundPackets1_19_4> soundRewriter = new SoundRewriter<>(this);
        soundRewriter.register1_19_3Sound(ClientboundPackets1_19_4.SOUND);
        soundRewriter.registerSound(ClientboundPackets1_19_4.ENTITY_SOUND);

        new StatisticsRewriter<>(this).register(ClientboundPackets1_19_4.STATISTICS);

        registerClientbound(ClientboundPackets1_19_4.SCOREBOARD_OBJECTIVE, wrapper -> {
            final byte slot = wrapper.read(Type.BYTE);
            wrapper.write(Type.VAR_INT, (int) slot);
        });

        registerServerbound(State.LOGIN, ServerboundLoginPackets.HELLO.getId(), ServerboundLoginPackets.HELLO.getId(), wrapper -> {
            wrapper.passthrough(Type.STRING); // Name

            final UUID uuid = wrapper.read(Type.UUID);
            wrapper.write(Type.OPTIONAL_UUID, uuid);
        });

        // Deal with the new CONFIGURATION protocol state the client expects
        // After the game profile is received by the client, it will send its login ack,
        // switch to the configration protocol state and send its brand.
        // We need to wait for it send the login ack before actually sending the play login,
        // hence packets are added to a queue. With the data from the login packet, we sent what is needed
        // during the configuration phase before finally transitioning to the play state with the client as well.
        registerClientbound(State.LOGIN, ClientboundLoginPackets.GAME_PROFILE.getId(), ClientboundLoginPackets.GAME_PROFILE.getId(), wrapper -> {
            wrapper.user().get(ConfigurationState.class).setBridgePhase(ConfigurationState.BridgePhase.PROFILE_SENT);
        });

        registerServerbound(State.LOGIN, ServerboundLoginPackets.LOGIN_ACKNOWLEDGED.getId(), -1, wrapper -> {
            System.out.println("Login acknowleged!");
            wrapper.cancel();
            final ConfigurationState configurationState = wrapper.user().get(ConfigurationState.class);
            configurationState.setBridgePhase(ConfigurationState.BridgePhase.CONFIGURATION);
            wrapper.user().getProtocolInfo().setState(State.PLAY);

            for (final ConfigurationState.QueuedPacket packet : configurationState.packetQueue()) {
                final PacketWrapper queuedWrapper;
                if (packet.packetType() != null) {
                    queuedWrapper = PacketWrapper.create(packet.packetType(), packet.buf(), wrapper.user());
                } else {
                    //noinspection deprecation
                    queuedWrapper = PacketWrapper.create(packet.packetId(), packet.buf(), wrapper.user());
                }

                queuedWrapper.send(Protocol1_20_2To1_20.class, false);
            }

            configurationState.packetQueue().clear();
        });
        cancelServerbound(State.LOGIN, ServerboundLoginPackets.CUSTOM_QUERY_ANSWER.getId()); // TODO ?

        // TODO Make sure this is called in other protocols as well/the base protocol
        registerServerbound(State.CONFIGURATION, ServerboundConfigurationPackets1_20_2.FINISH_CONFIGURATION.getId(), -1, wrapper -> {
            wrapper.user().get(ConfigurationState.class).reset();
            wrapper.cancel();
            System.out.println("CLIENT NOW ALSO ENTERING PLAY STATE");
        });

        registerServerbound(State.CONFIGURATION, ServerboundConfigurationPackets1_20_2.CUSTOM_PAYLOAD.getId(), -1, wrapper -> {
            wrapper.setPacketType(ServerboundPackets1_20_2.PLUGIN_MESSAGE);
            wrapper.user().get(ConfigurationState.class).addPacketToQueue(wrapper, false);
        });
        registerServerbound(State.CONFIGURATION, ServerboundConfigurationPackets1_20_2.KEEP_ALIVE.getId(), ServerboundPackets1_19_4.KEEP_ALIVE.getId(), wrapper -> {
        });
        registerServerbound(State.CONFIGURATION, ServerboundConfigurationPackets1_20_2.PONG.getId(), ServerboundPackets1_19_4.PONG.getId(), wrapper -> {
        });
        registerServerbound(State.CONFIGURATION, ServerboundConfigurationPackets1_20_2.RESOURCE_PACK.getId(), ServerboundPackets1_19_4.RESOURCE_PACK_STATUS.getId(), wrapper -> {
        });

        cancelClientbound(ClientboundPackets1_19_4.UPDATE_ENABLED_FEATURES); // Sad emoji
        cancelServerbound(ServerboundPackets1_20_2.CONFIGURATION_ACKNOWLEDGED);

        // TODO Check if we can just not send batches (probably fine like this)
        cancelServerbound(ServerboundPackets1_20_2.CHUNK_BATCH_RECEIVED);
    }

    @Override
    public void transform(final Direction direction, final State state, final PacketWrapper packetWrapper) throws Exception {
        final ConfigurationState configurationBridge = packetWrapper.user().get(ConfigurationState.class);
        if (configurationBridge.bridgePhase() == ConfigurationState.BridgePhase.NONE) {
            super.transform(direction, state, packetWrapper);
        }

        if (direction == Direction.SERVERBOUND) {
            // Client and server might be on two different protocol states - always let the client packets go through
            super.transform(direction, configurationBridge.bridgePhase() == ConfigurationState.BridgePhase.CONFIGURATION
                    ? State.CONFIGURATION : state, packetWrapper);
            return;
        }

        // Queue packets sent by the serverwhile we wait for the client to transition to the configuration state
        if (configurationBridge.bridgePhase() == ConfigurationState.BridgePhase.PROFILE_SENT) {
            System.out.println("added to queue " + packetWrapper.getId() + " " + direction + " " + state);
            configurationBridge.addPacketToQueue(packetWrapper, direction == Direction.CLIENTBOUND);
            throw CancelException.generate();
        }

        // Map some of them to their configuration state counterparts
        System.out.println("Transforming " + packetWrapper.getId() + " " + direction + " " + state);
        System.out.println(configurationBridge.bridgePhase());
        if (packetWrapper.getPacketType() == null || packetWrapper.getPacketType().state() != State.CONFIGURATION) {
            final int unmappedId = packetWrapper.getId();
            if (state == State.PLAY) {
                if (unmappedId == ClientboundPackets1_19_4.PLUGIN_MESSAGE.getId()) {
                    packetWrapper.setPacketType(ClientboundConfigurationPackets1_20_2.CUSTOM_PAYLOAD);
                } else if (unmappedId == ClientboundPackets1_19_4.DISCONNECT.getId()) {
                    packetWrapper.setPacketType(ClientboundConfigurationPackets1_20_2.DISCONNECT);
                } else if (unmappedId == ClientboundPackets1_19_4.KEEP_ALIVE.getId()) {
                    packetWrapper.setPacketType(ClientboundConfigurationPackets1_20_2.KEEP_ALIVE);
                } else if (unmappedId == ClientboundPackets1_19_4.PING.getId()) {
                    packetWrapper.setPacketType(ClientboundConfigurationPackets1_20_2.PING);
                } else if (unmappedId == ClientboundPackets1_19_4.RESOURCE_PACK.getId()) {
                    packetWrapper.setPacketType(ClientboundConfigurationPackets1_20_2.RESOURCE_PACK);
                } else if (unmappedId == ClientboundPackets1_19_4.UPDATE_ENABLED_FEATURES.getId()) {
                    packetWrapper.setPacketType(ClientboundConfigurationPackets1_20_2.UPDATE_ENABLED_FEATURES);
                } else if (unmappedId == ClientboundPackets1_19_4.TAGS.getId()) {
                    packetWrapper.setPacketType(ClientboundConfigurationPackets1_20_2.UPDATE_TAGS);
                } else {
                    configurationBridge.addPacketToQueue(packetWrapper, direction == Direction.CLIENTBOUND);
                    throw CancelException.generate();
                }
            }
        }


        // Redirect packets during the fake configuration phase
        // This might mess up people using Via API/other protocols down the line, but such is life. We can't have different states for server and client
        super.transform(direction, State.CONFIGURATION, packetWrapper);
    }

    @Override
    protected @Nullable ServerboundPackets1_20_2 configurationAcknowledgedPacket() {
        return null; // Don't handle it in the transitioning protocol
    }

    @Override
    public void init(final UserConnection user) {
        user.put(new ConfigurationState());
        addEntityTracker(user, new EntityTrackerBase(user, Entity1_19_4Types.PLAYER));
    }

    @Override
    public EntityRewriter<Protocol1_20_2To1_20> getEntityRewriter() {
        return entityPacketRewriter;
    }

    @Override
    public ItemRewriter<Protocol1_20_2To1_20> getItemRewriter() {
        return itemPacketRewriter;
    }
}