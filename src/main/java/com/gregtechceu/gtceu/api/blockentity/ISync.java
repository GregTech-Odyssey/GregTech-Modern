package com.gregtechceu.gtceu.api.blockentity;

import net.minecraftforge.network.PacketDistributor;

import com.gto.datasynclib.FieldDataManager;
import com.gto.datasynclib.IFieldDataHolder;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.network.BlockEntitySyncPacket;
import com.gto.datasynclib.network.DataSyncNetwork;
import com.gto.datasynclib.util.ByteBufUtil;

public interface ISync extends IFieldDataHolder {

    GTBlockEntity getHolder();

    default void remoteCall(String method, Object... args) {
        DataSyncNetwork.sendBlockEntityToClients(getHolder(), FieldDataManager.writeRemoteCall(getHolder(), this, method, args));
    }

    default void syncToServer() {
        DataSyncNetwork.CHANNEL.sendToServer(new BlockEntitySyncPacket(getHolder().getBlockPos(), ByteBufUtil.writeToBytes(ByteBufUtil.FRIENDLY_BYTE_BUF_WRAPPER, buf -> getHolder().getFieldDataManager().writeToNetworkBuffer(LogicalSide.CLIENT, buf, false))));
    }

    default void syncToClient() {
        DataSyncNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> getHolder().getChunk()), new BlockEntitySyncPacket(getHolder().getBlockPos(), ByteBufUtil.writeToBytes(ByteBufUtil.FRIENDLY_BYTE_BUF_WRAPPER, buf -> getHolder().getFieldDataManager().writeToNetworkBuffer(LogicalSide.SERVER, buf, false))));
    }

    default void syncAllToServer() {
        DataSyncNetwork.CHANNEL.sendToServer(new BlockEntitySyncPacket(getHolder().getBlockPos(), ByteBufUtil.writeToBytes(ByteBufUtil.FRIENDLY_BYTE_BUF_WRAPPER, buf -> getHolder().getFieldDataManager().writeToNetworkBuffer(LogicalSide.CLIENT, buf, true))));
    }

    default void syncAllToClient() {
        DataSyncNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> getHolder().getChunk()), new BlockEntitySyncPacket(getHolder().getBlockPos(), ByteBufUtil.writeToBytes(ByteBufUtil.FRIENDLY_BYTE_BUF_WRAPPER, buf -> getHolder().getFieldDataManager().writeToNetworkBuffer(LogicalSide.SERVER, buf, true))));
    }
}
