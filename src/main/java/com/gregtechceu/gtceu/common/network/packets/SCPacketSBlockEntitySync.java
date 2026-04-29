package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.gto.datasynclib.IFieldDataHolder;
import com.gto.datasynclib.LogicalSide;

public class SCPacketSBlockEntitySync implements IPacket {

    private long pos;
    private byte[] data;

    public SCPacketSBlockEntitySync(long pos, byte[] data) {
        this.pos = pos;
        this.data = data;
    }

    public static SCPacketSBlockEntitySync of(BlockEntity blockEntity, boolean force) {
        return new SCPacketSBlockEntitySync(blockEntity.getBlockPos().asLong(), ((IFieldDataHolder) blockEntity).getFieldDataManager().writeToNetworkBuffer(LogicalSide.SERVER, force));
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeByteArray(data);
        buf.writeVarLong(pos);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        data = buf.readByteArray();
        pos = buf.readVarLong();
    }

    @Override
    public void execute(IHandlerContext handler) {
        var level = GTUtil.getClientLevel();
        if (level != null && level.getBlockEntity(BlockPos.of(pos)) instanceof IFieldDataHolder holder) {
            holder.getFieldDataManager().readFromNetworkBuffer(LogicalSide.CLIENT, data);
        }
    }

    public SCPacketSBlockEntitySync() {}
}
