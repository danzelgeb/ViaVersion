/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaVersion
 * Copyright (C) 2016-2024 ViaVersion and contributors
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
package com.viaversion.viaversion.protocols.protocol1_16_2to1_16_1.metadata;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.entities.EntityTypes1_16;
import com.viaversion.viaversion.api.minecraft.entities.EntityTypes1_16_2;
import com.viaversion.viaversion.api.minecraft.entities.EntityType;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.minecraft.metadata.Metadata;
import com.viaversion.viaversion.api.minecraft.Particle;
import com.viaversion.viaversion.api.type.types.version.Types1_16;
import com.viaversion.viaversion.protocols.protocol1_16_2to1_16_1.Protocol1_16_2To1_16_1;
import com.viaversion.viaversion.protocols.protocol1_16to1_15_2.ClientboundPackets1_16;
import com.viaversion.viaversion.rewriter.EntityRewriter;
import java.util.List;

public class MetadataRewriter1_16_2To1_16_1 extends EntityRewriter<ClientboundPackets1_16, Protocol1_16_2To1_16_1> {

    public MetadataRewriter1_16_2To1_16_1(Protocol1_16_2To1_16_1 protocol) {
        super(protocol);
        mapTypes(EntityTypes1_16.values(), EntityTypes1_16_2.class);
    }

    @Override
    public void handleMetadata(int entityId, EntityType type, Metadata metadata, List<Metadata> metadatas, UserConnection connection) throws Exception {
        if (metadata.metaType() == Types1_16.META_TYPES.itemType) {
            protocol.getItemRewriter().handleItemToClient((Item) metadata.getValue());
        } else if (metadata.metaType() == Types1_16.META_TYPES.blockStateType) {
            int data = (int) metadata.getValue();
            metadata.setValue(protocol.getMappingData().getNewBlockStateId(data));
        } else if (metadata.metaType() == Types1_16.META_TYPES.particleType) {
            rewriteParticle((Particle) metadata.getValue());
        }

        if (type == null) return;

        if (type.isOrHasParent(EntityTypes1_16_2.MINECART_ABSTRACT)
                && metadata.id() == 10) {
            // Convert to new block id
            int data = (int) metadata.getValue();
            metadata.setValue(protocol.getMappingData().getNewBlockStateId(data));
        }

        if (type.isOrHasParent(EntityTypes1_16_2.ABSTRACT_PIGLIN)) {
            if (metadata.id() == 15) {
                metadata.setId(16);
            } else if (metadata.id() == 16) {
                metadata.setId(15);
            }
        }
    }

    @Override
    public EntityType typeFromId(int type) {
        return EntityTypes1_16_2.getTypeFromId(type);
    }
}
