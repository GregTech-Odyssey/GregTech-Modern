package com.gregtechceu.gtceu.api.transfer.key;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import appeng.api.storage.MEStorage;
import org.jetbrains.annotations.Nullable;

/**
 * 可以对外提供 {@link MEStorage} 的方块实体（机器）。
 *
 * <p>
 * 与 {@code MetaMachine#getStorageCap(Direction)} 的区别是语义：这个接口表示「我是一个存储容器，
 * 可以被世界级索引收录、被其它设备（例如合成站）当作料源读取」，而不是「我这个面能不能被管道接入」。
 * 实现方通常是箱子/柜子这类容器机器。
 *
 * <p>
 * 收录方式：容器机器在 {@code onLoad} 时把自己注册进
 * {@link MEStorageProviderIndex}（世界级八叉树索引），在 {@code onUnload}/{@code onMachineRemoved}
 * 时注销。索引按位置用八叉树存储，查询方（例如合成站）直接按范围查索引，不需要再逐格扫描方块。
 */
public interface MEStorageProvider {

    /**
     * @param side 读取面；{@code null} 表示「不限面」（容器的整体库存视图）
     * @return 存储视图，读不到时返回 {@code null}
     */
    @Nullable
    MEStorage getStorageCap(@Nullable Direction side);

    /** 该容器在世界中的位置，用于索引 key。 */
    BlockPos getPos();
}
