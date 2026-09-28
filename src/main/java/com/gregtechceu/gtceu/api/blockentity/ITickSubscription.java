package com.gregtechceu.gtceu.api.blockentity;

import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.misc.TickTimeMonitor;
import com.gregtechceu.gtceu.utils.TaskHandler;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

public interface ITickSubscription {

    static <T extends ISubscription> T unsubscribe(@Nullable T current) {
        if (current != null) {
            current.unsubscribe();
        }
        return null;
    }

    GTBlockEntity getHolder();

    /**
     * For initialization. To get level and property fields after auto sync, you can subscribe it in
     * {@link BlockEntity#clearRemoved()} event.
     */
    @Nullable
    default TickableSubscription subscribeServerTick(Runnable runnable, int cycle) {
        var self = getHolder();
        if (self.getLevel() instanceof ServerLevel serverLevel) {
            return TaskHandler.enqueueTick(serverLevel, self.isRemove, runnable, cycle, self.tickDelay);
        }
        return null;
    }

    @Nullable
    default TickableSubscription subscribeClientTick(Runnable runnable, int cycle) {
        var self = getHolder();
        var level = self.getLevel();
        if (level != null && level.isClientSide) {
            return TaskHandler.enqueueTick(level, self.isRemove, runnable, cycle, self.tickDelay);
        }
        return null;
    }

    @Nullable
    default TickableSubscription subscribeAsyncTick(Runnable runnable, int cycle) {
        var self = getHolder();
        var level = self.getLevel();
        if (level != null) {
            return TaskHandler.enqueueAsyncTick(level, self.isRemove, runnable, cycle, self.tickDelay);
        }
        return null;
    }

    default TickableSubscription subscribeServerTick(@Nullable TickableSubscription last, Runnable runnable) {
        return subscribeServerTick(last, runnable, 0);
    }

    default TickableSubscription subscribeClientTick(@Nullable TickableSubscription last, Runnable runnable) {
        return subscribeClientTick(last, runnable, 0);
    }

    default TickableSubscription subscribeAsyncTick(@Nullable TickableSubscription last, Runnable runnable) {
        return subscribeAsyncTick(last, runnable, 0);
    }

    @Nullable
    default TickableSubscription subscribeServerTick(@Nullable TickableSubscription last, Runnable runnable, int cycle) {
        if (last == null || !last.stillSubscribed) {
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
    default TickableSubscription subscribeMonitoredTick(@Nullable TickableSubscription last,
                                                        TickTimeMonitor.Entry entry, Runnable task, int cycle) {
        return subscribeServerTick(last, getHolder().monitorTick(entry, task), cycle);
    }

    @Nullable
    default TickableSubscription subscribeClientTick(@Nullable TickableSubscription last, Runnable runnable, int cycle) {
        if (last == null || !last.stillSubscribed) {
            return subscribeClientTick(runnable, cycle);
        }
        return last;
    }

    @Nullable
    default TickableSubscription subscribeAsyncTick(@Nullable TickableSubscription last, Runnable runnable, int cycle) {
        if (last == null || !last.stillSubscribed) {
            return subscribeAsyncTick(runnable, cycle);
        }
        return last;
    }
}
