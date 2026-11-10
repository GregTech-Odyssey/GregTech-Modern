package com.gregtechceu.gtceu.api.blockentity;

import com.gregtechceu.gtceu.api.misc.TickTimeMonitor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.suried.gtoutils.schedule.Cadence;
import com.suried.gtoutils.schedule.Schedulers;
import com.suried.gtoutils.schedule.Task;
import org.jetbrains.annotations.Nullable;

public interface ITickSubscription {

    GTBlockEntity getHolder();

    /**
     * For initialization. To get level and property fields after auto sync, you can subscribe it in
     * {@link BlockEntity#clearRemoved()} event.
     */
    @Nullable
    default Task subscribeServerTick(Runnable runnable, int cycle) {
        var self = getHolder();
        if (self.getLevel() instanceof ServerLevel serverLevel) {
            return Schedulers.of(serverLevel).repeat(Cadence.every(cycle), self, runnable);
        }
        return null;
    }

    @Nullable
    default Task subscribeClientTick(Runnable runnable, int cycle) {
        var self = getHolder();
        var level = self.getLevel();
        if (level != null && level.isClientSide) {
            return Schedulers.of(level).repeat(Cadence.every(cycle), self, runnable);
        }
        return null;
    }

    @Nullable
    default Task subscribeAsyncTick(Runnable runnable, int cycle) {
        var self = getHolder();
        var level = self.getLevel();
        if (level != null) {
            return Schedulers.async(level).repeat(Cadence.every(cycle), self, runnable);
        }
        return null;
    }

    default Task subscribeServerTick(@Nullable Task last, Runnable runnable) {
        return subscribeServerTick(last, runnable, 0);
    }

    default Task subscribeClientTick(@Nullable Task last, Runnable runnable) {
        return subscribeClientTick(last, runnable, 0);
    }

    default Task subscribeAsyncTick(@Nullable Task last, Runnable runnable) {
        return subscribeAsyncTick(last, runnable, 0);
    }

    @Nullable
    default Task subscribeServerTick(@Nullable Task last, Runnable runnable, int cycle) {
        if (!Task.isActive(last)) {
            return subscribeServerTick(runnable, cycle);
        }
        return last;
    }

    /**
     * 带耗时监控的订阅：同一个 {@code entry} 复用同一个监控器（在方块实体上按 key 的注册 id 缓存）。
     *
     * <p>
     * 没人看（Jade 没在取数据）时和普通订阅几乎一样，只是一个 boolean 判断；玩家查看该方块时才会计时，
     * 并在 Jade 信息里按 {@code entry} 显示一行。
     *
     * <p>
     * <b>{@code task} 必须是固定的</b>（例如 {@code this::autoOutput}），不能是捕获了方法参数 / 临时对象的
     * lambda：监控只在第一次注册时记住它，之后一直跑旧的，既会泄漏被捕获的引用，也会丢掉不变量更新。
     */
    @Nullable
    default Task subscribeMonitoredTick(@Nullable Task last,
                                        TickTimeMonitor.Entry entry, Runnable task, int cycle) {
        return subscribeServerTick(last, getHolder().monitorTick(entry, task), cycle);
    }

    @Nullable
    default Task subscribeClientTick(@Nullable Task last, Runnable runnable, int cycle) {
        if (!Task.isActive(last)) {
            return subscribeClientTick(runnable, cycle);
        }
        return last;
    }

    @Nullable
    default Task subscribeAsyncTick(@Nullable Task last, Runnable runnable, int cycle) {
        if (!Task.isActive(last)) {
            return subscribeAsyncTick(runnable, cycle);
        }
        return last;
    }
}
