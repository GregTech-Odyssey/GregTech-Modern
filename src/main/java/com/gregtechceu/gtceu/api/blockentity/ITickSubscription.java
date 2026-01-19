package com.gregtechceu.gtceu.api.blockentity;

import com.gregtechceu.gtceu.api.machine.TickableSubscription;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

public interface ITickSubscription {

    /**
     * For initialization. To get level and property fields after auto sync, you can subscribe it in
     * {@link BlockEntity#clearRemoved()} event.
     */
    @Nullable
    TickableSubscription subscribeServerTick(Runnable runnable, int cycle);

    @Nullable
    TickableSubscription subscribeClientTick(Runnable runnable, int cycle);

    static <T extends ISubscription> T unsubscribe(@Nullable T current) {
        if (current != null) {
            current.unsubscribe();
        }
        return null;
    }

    default TickableSubscription subscribeServerTick(@Nullable TickableSubscription last, Runnable runnable) {
        return subscribeServerTick(last, runnable, 0);
    }

    default TickableSubscription subscribeClientTick(@Nullable TickableSubscription last, Runnable runnable) {
        return subscribeClientTick(last, runnable, 0);
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
}
