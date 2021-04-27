/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaVersion
 * Copyright (C) 2016-2021 ViaVersion and contributors
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
package com.viaversion.viaversion.bukkit.handlers;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.protocols.protocol1_9to1_8.Protocol1_9To1_8;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

public class BukkitPacketHandler extends MessageToMessageEncoder {
    private final UserConnection info;

    public BukkitPacketHandler(UserConnection info) {
        this.info = info;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object o, List list) throws Exception {
        // Split chunks bulk packet up in to single chunks packets before it reached the encoder.
        // This will prevent issues with several plugins and other protocol handlers due to the chunks being sent twice.
        // It also sends the chunks in the right order possible resolving some issues with added chunks/block/entity data.
        if (!(o instanceof ByteBuf)) {
            info.getPacketTracker().setLastPacket(o);
            /* This transformer is more for fixing issues which we find hard at packet level :) */
            if (info.isActive() && filter(o, list)) {
                return;
            }
        }

        list.add(o);
    }

    @Deprecated
    public boolean filter(Object o, List list) throws Exception {
        if (info.getProtocolInfo().getPipeline().contains(Protocol1_9To1_8.class)) {
            Protocol1_9To1_8 protocol = Via.getManager().getProtocolManager().getProtocol(Protocol1_9To1_8.class);
            if (protocol.isFiltered(o.getClass())) {
                protocol.filterPacket(info, o, list);
                return true;
            }
        }
        return false;
    }
}
