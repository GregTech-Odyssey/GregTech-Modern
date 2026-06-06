package com.gregtechceu.gtceu.api.blockentity;

import com.gregtechceu.gtceu.common.network.GTNetwork;
import com.gregtechceu.gtceu.common.network.packets.CSPacketSBlockEntitySync;
import com.gregtechceu.gtceu.common.network.packets.SCPacketSBlockEntitySync;

import com.gto.datasynclib.IFieldDataHolder;

public interface ISync extends IFieldDataHolder {

    GTBlockEntity getHolder();

    default void syncToServer() {
        GTNetwork.NETWORK.sendToServer(CSPacketSBlockEntitySync.of(getHolder(), false));
    }

    default void syncToClient() {
        GTNetwork.NETWORK.sendToTrackingChunk(SCPacketSBlockEntitySync.of(getHolder(), false), getHolder().getChunk());
    }

    default void syncAllToServer() {
        GTNetwork.NETWORK.sendToServer(CSPacketSBlockEntitySync.of(getHolder(), true));
    }

    default void syncAllToClient() {
        GTNetwork.NETWORK.sendToTrackingChunk(SCPacketSBlockEntitySync.of(getHolder(), true), getHolder().getChunk());
    }
}
