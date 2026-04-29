package com.gregtechceu.gtceu.common.network.packets;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.gto.datasynclib.IFieldDataHolder;
import com.gto.datasynclib.LogicalSide;

public class CSPacketSBlockEntitySync implements IPacket {

    private long pos;
    private byte[] data;

    public CSPacketSBlockEntitySync(long pos, byte[] data) {
        this.pos = pos;
        this.data = data;
    }

    public static CSPacketSBlockEntitySync of(BlockEntity blockEntity, boolean force) {
        return new CSPacketSBlockEntitySync(blockEntity.getBlockPos().asLong(), ((IFieldDataHolder) blockEntity).getFieldDataManager().writeToNetworkBuffer(LogicalSide.CLIENT, force));
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
        if (handler.getPlayer() instanceof ServerPlayer serverPlayer && serverPlayer.level().getBlockEntity(BlockPos.of(pos)) instanceof IFieldDataHolder holder) {
            holder.getFieldDataManager().readFromNetworkBuffer(LogicalSide.SERVER, data);
        }
    }

    public CSPacketSBlockEntitySync() {}
}
