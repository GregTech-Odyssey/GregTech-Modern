package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;

import net.minecraft.network.FriendlyByteBuf;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

public class SCPacketUpdateActiveBlock implements IPacket {

    private LongSet blocks;
    private boolean active;

    public SCPacketUpdateActiveBlock(LongSet blocks, boolean active) {
        this.blocks = blocks;
        this.active = active;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeVarInt(blocks.size());
        blocks.forEach(buf::writeVarLong);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        active = buf.readBoolean();
        int size = buf.readVarInt();
        blocks = new LongOpenHashSet(size);
        for (int i = 0; i < size; i++) {
            blocks.add(buf.readVarLong());
        }
    }

    @Override
    public void execute(IHandlerContext handler) {
        ActiveBlock.updateActiveBlocks(blocks, GTUtil.getClientLevel(), active);
    }

    public SCPacketUpdateActiveBlock() {}
}
