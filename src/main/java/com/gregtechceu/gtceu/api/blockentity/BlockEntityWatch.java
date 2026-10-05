package com.gregtechceu.gtceu.api.blockentity;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.core.ILevel;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;

/**
 * 按位置订阅方块实体的出现、移除（含区块卸载）、方块状态变化与存储暴露变化；订阅者弱引用，回调在事件现场同步发生，只应标记失效或排任务。
 * 维度内没有订阅时事件只多一次判空；同一位置的回调里再次触发该位置的事件会被忽略。
 */
public final class BlockEntityWatch {

    @FunctionalInterface
    public interface Listener {

        void onBlockEntityChanged(BlockEntity blockEntity);
    }

    private final ILevel owner;
    private final Long2ObjectOpenHashMap<Ref[]> watchers = new Long2ObjectOpenHashMap<>();
    private final ReferenceQueue<Listener> queue = new ReferenceQueue<>();
    private long[] firing = new long[4];
    private int depth;

    private BlockEntityWatch(ILevel owner) {
        this.owner = owner;
    }

    public static void watch(Level level, BlockPos pos, Listener listener) {
        var owner = (ILevel) level;
        var watch = owner.gtceu$getBlockEntityWatch();
        if (watch == null) owner.gtceu$setBlockEntityWatch(watch = new BlockEntityWatch(owner));
        watch.add(pos.asLong(), listener);
        watch.purge();
    }

    public static void unwatch(Level level, BlockPos pos, Listener listener) {
        var watch = ((ILevel) level).gtceu$getBlockEntityWatch();
        if (watch == null) return;
        watch.remove(pos.asLong(), listener);
        watch.purge();
    }

    public static void changed(BlockEntity blockEntity) {
        var level = blockEntity.getLevel();
        if (level == null) return;
        var watch = ((ILevel) level).gtceu$getBlockEntityWatch();
        if (watch != null) watch.fire(blockEntity);
    }

    private void fire(BlockEntity blockEntity) {
        long pos = blockEntity.getBlockPos().asLong();
        var refs = watchers.get(pos);
        if (refs == null) return;
        int d = depth;
        var f = firing;
        for (int i = 0; i < d; i++) {
            if (f[i] == pos) return;
        }
        if (d == f.length) firing = f = Arrays.copyOf(f, d << 1);
        f[d] = pos;
        depth = d + 1;
        boolean stale = false;
        try {
            for (var ref : refs) {
                var listener = ref.get();
                if (listener == null) {
                    stale = true;
                    continue;
                }
                try {
                    listener.onBlockEntityChanged(blockEntity);
                } catch (RuntimeException e) {
                    GTCEu.LOGGER.error("Block entity watcher failed at {}", blockEntity.getBlockPos(), e);
                }
            }
        } finally {
            depth = d;
        }
        if (stale) purge();
    }

    private void add(long pos, Listener listener) {
        var refs = watchers.get(pos);
        int n = 0;
        if (refs == null) {
            refs = new Ref[1];
        } else {
            for (var ref : refs) {
                if (ref.get() == listener) return;
            }
            n = refs.length;
            refs = Arrays.copyOf(refs, n + 1);
        }
        refs[n] = new Ref(listener, pos, queue);
        watchers.put(pos, refs);
    }

    private void remove(long pos, Listener listener) {
        var refs = watchers.get(pos);
        if (refs == null) return;
        for (var ref : refs) {
            if (ref.get() == listener) {
                ref.clear();
                drop(pos, refs, ref);
                return;
            }
        }
    }

    private void purge() {
        Ref ref;
        while ((ref = (Ref) queue.poll()) != null) {
            var refs = watchers.get(ref.pos);
            if (refs != null) drop(ref.pos, refs, ref);
        }
        if (watchers.isEmpty() && owner.gtceu$getBlockEntityWatch() == this) owner.gtceu$setBlockEntityWatch(null);
    }

    private void drop(long pos, Ref[] refs, Ref ref) {
        int n = refs.length;
        for (int i = 0; i < n; i++) {
            if (refs[i] != ref) continue;
            if (n == 1) {
                watchers.remove(pos);
            } else {
                var next = new Ref[n - 1];
                System.arraycopy(refs, 0, next, 0, i);
                System.arraycopy(refs, i + 1, next, i, n - i - 1);
                watchers.put(pos, next);
            }
            return;
        }
    }

    private static final class Ref extends WeakReference<Listener> {

        private final long pos;

        private Ref(Listener listener, long pos, ReferenceQueue<Listener> queue) {
            super(listener, queue);
            this.pos = pos;
        }
    }
}
