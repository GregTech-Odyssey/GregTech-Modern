package com.gto.datasynclib;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datasream.data.MapData;

public interface IFieldDataHolder {

    FieldDataManager getFieldDataManager();

    /**
     * 安排更新操作
     * <p>
     * 当字段数据发生变化并需要通知时调用此方法。
     * 默认实现为空，子类可以覆盖以实现自定义的更新逻辑。
     * 
     * @param side 逻辑端（客户端或服务端），用于区分更新方向
     */
    default void scheduleUpdate(LogicalSide side) {}

    /**
     * 写入自定义同步数据
     * <p>
     * 在常规字段同步之前写入额外的自定义数据到网络缓冲区。
     * 默认实现为空，子类可以覆盖以实现自定义数据同步。
     * 
     * @param buf   网络数据缓冲区
     * @param force 是否强制写入所有数据（忽略脏标记）
     */
    default void writeCustomSyncData(FriendlyByteBuf buf, boolean force) {}

    /**
     * 读取自定义同步数据
     * <p>
     * 在常规字段同步之前从网络缓冲区读取额外的自定义数据。
     * 默认实现为空，子类可以覆盖以实现自定义数据同步。
     * 
     * @param buf 网络数据缓冲区
     */
    default void readCustomSyncData(FriendlyByteBuf buf) {}

    /**
     * 写入自定义保存数据
     * <p>
     * 在常规字段保存之前写入额外的自定义数据到MapData。
     * 默认实现为空，子类可以覆盖以实现自定义数据持久化。
     * 
     * @param data 数据映射对象，用于存储持久化数据
     */
    default void writeCustomSaveData(MapData data) {}

    /**
     * 读取自定义保存数据
     * <p>
     * 在常规字段加载之前从MapData读取额外的自定义数据。
     * 默认实现为空，子类可以覆盖以实现自定义数据持久化。
     * 
     * @param data 数据映射对象，包含持久化数据
     */
    default void readCustomSaveData(MapData data) {}
}
