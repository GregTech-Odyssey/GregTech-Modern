package com.gregtechceu.gtceu.api.transfer.key;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityWatch;
import com.gregtechceu.gtceu.core.ILevel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.StorageAccess;
import org.jetbrains.annotations.Nullable;

/**
 * 按位置指定的取用目标（超立方体、无线传输等远端转发）：只取已加载区块里的方块实体，按面各用一个 {@link KeyTarget}；
 * 订阅后目标出现、移除、方块状态或存储暴露变化时经 {@link BlockEntityWatch} 回调订阅者（设施弱持本对象，本对象强持订阅者）。
 */
public final class RemoteKeyTarget implements BlockEntityWatch.Listener {

    @FunctionalInterface
    public interface Listener {

        void onTargetChanged(RemoteKeyTarget target);
    }

    private final KeyTarget[] targets = new KeyTarget[7];
    @Nullable
    private Level level;
    @Nullable
    private BlockPos pos;
    @Nullable
    private BlockEntity blockEntity;
    @Nullable
    private Listener listener;

    public RemoteKeyTarget() {}

    public RemoteKeyTarget(@Nullable Level level, @Nullable BlockPos pos) {
        bind(level, pos);
    }

    public void bind(@Nullable Level level, @Nullable BlockPos pos) {
        if (this.level == level && (pos == null ? this.pos == null : pos.equals(this.pos))) return;
        unwatch();
        this.level = level;
        this.pos = pos == null ? null : pos.immutable();
        release(null);
        watch();
    }

    public void subscribe(@Nullable Listener listener) {
        if (this.listener == listener) return;
        unwatch();
        this.listener = listener;
        watch();
    }

    private void watch() {
        var l = level;
        var p = pos;
        if (listener != null && l != null && p != null) BlockEntityWatch.watch(l, p, this);
    }

    private void unwatch() {
        var l = level;
        var p = pos;
        if (listener != null && l != null && p != null) BlockEntityWatch.unwatch(l, p, this);
    }

    @Override
    public void onBlockEntityChanged(BlockEntity be) {
        if (be.isRemoved()) {
            if (be == blockEntity) release(null);
        } else if (be != blockEntity) {
            release(be);
        }
        var l = listener;
        if (l != null) l.onTargetChanged(this);
    }

    public @Nullable Level level() {
        return level;
    }

    public @Nullable BlockPos pos() {
        return pos;
    }

    public @Nullable BlockEntity blockEntity() {
        var be = blockEntity;
        if (be != null && !be.isRemoved()) return be;
        var l = level;
        var p = pos;
        be = l == null || p == null ? null : ILevel.getCachedBlockEntity(l, p);
        if (blockEntity != be) release(be);
        return be;
    }

    private void release(@Nullable BlockEntity next) {
        if (blockEntity != null) {
            for (var t : targets) {
                if (t != null) t.clear();
            }
        }
        blockEntity = next;
    }

    private KeyTarget target(@Nullable Direction side) {
        int i = side == null ? 6 : side.ordinal();
        var t = targets[i];
        if (t == null) targets[i] = t = new KeyTarget();
        return t;
    }

    public @Nullable IKeyHandler<?> find(@Nullable Direction side, AEKeyType type, StorageAccess access) {
        var be = blockEntity();
        return be == null ? null : target(side).find(be, side, type, access);
    }

    @SuppressWarnings("unchecked")
    public @Nullable IKeyHandler<AEItemKey> items(@Nullable Direction side, StorageAccess access) {
        return (IKeyHandler<AEItemKey>) find(side, AEKeyTypes.ITEMS, access);
    }

    @SuppressWarnings("unchecked")
    public @Nullable IKeyHandler<AEFluidKey> fluids(@Nullable Direction side, StorageAccess access) {
        return (IKeyHandler<AEFluidKey>) find(side, AEKeyTypes.FLUIDS, access);
    }

    public boolean has(@Nullable Direction side, AEKeyType type, StorageAccess access) {
        var be = blockEntity();
        return be != null && target(side).has(be, side, type, access);
    }
}
