package com.gto.datasynclib;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datasream.data.MapData;

public interface IFieldDataHolder {

    FieldDataManager getFieldDataManager();

    default void scheduleUpdate(LogicalSide side) {}

    default void writeCustomSyncData(FriendlyByteBuf buf, boolean force) {}

    default void readCustomSyncData(FriendlyByteBuf buf) {}

    default void writeCustomSaveData(MapData data) {}

    default void readCustomSaveData(MapData data) {}
}
