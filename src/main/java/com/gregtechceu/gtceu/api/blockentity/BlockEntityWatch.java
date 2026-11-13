package com.gregtechceu.gtceu.api.blockentity;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.suried.gtoutils.level.LevelStore;
import com.suried.gtoutils.level.LoadedChunks;
import com.suried.gtoutils.schedule.KeyedSignal;
import com.suried.gtoutils.schedule.Schedulers;
import com.suried.gtoutils.schedule.Scope;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * 按位置关注方块实体的出现、移除（含区块卸载）、方块状态变化与存储暴露变化，只有作用域一种用法：派生里用 at、machineAt 读已加载的方块实体，读即追踪该位置；要当场反应的用 listen 挂在作用域上，作用域结束自动撤销。
 * 监听在事件现场同步执行并拿到触发事件的方块实体（出现事件发生时它还没进区块表），只应标记失效或排任务；同一位置的监听里再次触发该位置的事件会被忽略；别的线程上的事件转投到维度车道；维度内没人关注时事件只多一次判空。
 */
public final class BlockEntityWatch {

    private static final LevelStore<BlockEntityWatch> WATCHES = LevelStore.createWithLevel(BlockEntityWatch::new);

    private final Level owner;
    private final KeyedSignal<BlockPos> signals = new KeyedSignal<>();
    private long[] firing = new long[4];
    private int depth;
    private BlockEntity current;

    private BlockEntityWatch(Level owner) {
        this.owner = owner;
    }

    @Nullable
    public static BlockEntity at(Level level, BlockPos pos) {
        WATCHES.get(level).signals.track(pos.immutable());
        var blockEntity = LoadedChunks.blockEntity(level, pos);
        return blockEntity == null || blockEntity.isRemoved() ? null : blockEntity;
    }

    @Nullable
    public static MetaMachine machineAt(Level level, BlockPos pos) {
        return MetaMachine.getMachine(at(level, pos));
    }

    public static void listen(Scope scope, Level level, BlockPos pos, Consumer<? super BlockEntity> listener) {
        var watch = WATCHES.get(level);
        watch.signals.listen(scope, pos.immutable(), () -> {
            var blockEntity = watch.current;
            if (blockEntity != null) listener.accept(blockEntity);
        });
    }

    public static int watchedPositions(Level level) {
        var watch = WATCHES.getIfPresent(level);
        return watch == null ? 0 : watch.signals.keyCount();
    }

    public static void changed(BlockEntity blockEntity) {
        var level = blockEntity.getLevel();
        if (level == null) return;
        var watch = WATCHES.getIfPresent(level);
        if (watch != null && watch.signals.keyCount() != 0) watch.fire(blockEntity);
    }

    private void fire(BlockEntity blockEntity) {
        var lane = Schedulers.of(owner);
        if (lane.foreign()) {
            lane.schedule(0, null, () -> fire(blockEntity));
            return;
        }
        var pos = blockEntity.getBlockPos();
        long packed = pos.asLong();
        int d = depth;
        var f = firing;
        for (int i = 0; i < d; i++) {
            if (f[i] == packed) return;
        }
        if (d == f.length) firing = f = Arrays.copyOf(f, d << 1);
        f[d] = packed;
        depth = d + 1;
        var outer = current;
        current = blockEntity;
        try {
            signals.emit(pos);
        } finally {
            current = outer;
            depth = d;
        }
    }
}
