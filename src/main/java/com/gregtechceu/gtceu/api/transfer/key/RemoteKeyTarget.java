package com.gregtechceu.gtceu.api.transfer.key;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityWatch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.StorageAccess;
import com.suried.gtoutils.level.LoadedChunks;
import com.suried.gtoutils.schedule.Scope;
import org.jetbrains.annotations.Nullable;

/**
 * 按位置指定的取用目标（超立方体、无线传输等远端转发）：只取已加载区块里的方块实体，按面各用一个 {@link KeyTarget}；
 * 所有者用 mount 把它挂到自己的作用域上，目标出现、移除、方块状态或存储暴露变化时先丢掉旧缓存再通知所有者，作用域结束自动撤销。
 */
public final class RemoteKeyTarget {

    private final KeyTarget[] targets = new KeyTarget[7];
    @Nullable
    private Level level;
    @Nullable
    private BlockPos pos;
    @Nullable
    private BlockEntity blockEntity;

    public RemoteKeyTarget() {}

    public RemoteKeyTarget(@Nullable Level level, @Nullable BlockPos pos) {
        bind(level, pos);
    }

    public boolean bind(@Nullable Level level, @Nullable BlockPos pos) {
        if (this.level == level && (pos == null ? this.pos == null : pos.equals(this.pos))) return false;
        this.level = level;
        this.pos = pos == null ? null : pos.immutable();
        release(null);
        return true;
    }

    public void mount(Scope scope, Runnable changed) {
        var l = level;
        var p = pos;
        if (l == null || p == null) return;
        BlockEntityWatch.listen(scope, l, p, be -> {
            if (level != l || !p.equals(pos)) return;
            if (be.isRemoved()) {
                if (be == blockEntity) release(null);
            } else if (be != blockEntity) {
                release(be);
            }
            changed.run();
        });
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
        be = l == null || p == null ? null : LoadedChunks.blockEntity(l, p);
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
