package com.gregtechceu.gtceu.api.blockentity;

import com.gregtechceu.gtceu.api.machine.TickableSubscription;
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

    TickBlockEntity getHolder();

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
