package com.gregtechceu.gtceu.api.blockentity;

import net.minecraftforge.network.PacketDistributor;

import com.gto.datasynclib.IFieldDataHolder;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.network.BlockEntitySyncPacket;
import com.gto.datasynclib.network.DataSyncNetwork;

public interface ISync extends IFieldDataHolder {

    GTBlockEntity getHolder();

    default void syncToServer() {
        DataSyncNetwork.CHANNEL.sendToServer(new BlockEntitySyncPacket(getHolder().getBlockPos(), getHolder().getFieldDataManager().writeToNetworkBuffer(LogicalSide.CLIENT, false)));
    }

    default void syncToClient() {
        DataSyncNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> getHolder().getChunk()), new BlockEntitySyncPacket(getHolder().getBlockPos(), getHolder().getFieldDataManager().writeToNetworkBuffer(LogicalSide.SERVER, false)));
    }

    default void syncAllToServer() {
        DataSyncNetwork.CHANNEL.sendToServer(new BlockEntitySyncPacket(getHolder().getBlockPos(), getHolder().getFieldDataManager().writeToNetworkBuffer(LogicalSide.CLIENT, true)));
    }

    default void syncAllToClient() {
        DataSyncNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> getHolder().getChunk()), new BlockEntitySyncPacket(getHolder().getBlockPos(), getHolder().getFieldDataManager().writeToNetworkBuffer(LogicalSide.SERVER, true)));
    }
}
