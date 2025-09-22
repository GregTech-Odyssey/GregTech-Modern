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
    TickableSubscription subscribeServerTick(Runnable runnable);

    static ISubscription unsubscribe(@Nullable ISubscription current) {
        if (current != null) {
            current.unsubscribe();
        }
        return null;
    }

    @Nullable
    default TickableSubscription subscribeServerTick(@Nullable TickableSubscription last, Runnable runnable) {
        if (last == null || !last.isStillSubscribed()) {
            return subscribeServerTick(runnable);
        }
        return last;
    }
}
