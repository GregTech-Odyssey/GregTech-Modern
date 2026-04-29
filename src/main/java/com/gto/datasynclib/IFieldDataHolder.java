package com.gto.datasynclib;

import com.gto.datasynclib.datasream.data.MapData;
import com.gto.datasynclib.datasream.stream.ByteDataStream;

public interface IFieldDataHolder {

    FieldDataManager getFieldDataManager();

    default void scheduleUpdate(LogicalSide side) {}

    default void writeCustomSyncData(ByteDataStream buf, boolean force) {}

    default void readCustomSyncData(ByteDataStream buf) {}

    default void writeCustomSaveData(MapData data) {}

    default void readCustomSaveData(MapData data) {}
}
