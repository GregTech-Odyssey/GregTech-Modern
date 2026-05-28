package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.blockentity.ITickSubscription;
import com.gregtechceu.gtceu.utils.TaskHandler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import lombok.Getter;

import java.util.function.BooleanSupplier;

/**
 * Handles a subscription that is only active in specific conditions.
 * <p>
 * When the subscription is not currently active, it will be removed from the event loop, in order to not unnecessarily
 * consume resources.
 */
public class ConditionalSubscriptionHandler {

    private final ITickSubscription handler;
    private final Runnable runnable;
    private final BooleanSupplier condition;

    private TickableSubscription subscription;

    @Getter
    private int cycle;

    public ConditionalSubscriptionHandler(ITickSubscription handler, Runnable runnable, int cycle, BooleanSupplier condition) {
        this.handler = handler;
        this.runnable = runnable;
        this.condition = condition;
        this.cycle = cycle;
    }

    /**
     * Initializes the subscription and, if the supplied level is server-side, adds it to the event loop.
     *
     * @param level The level to create the subscription in.
     */
    public void initialize(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            this.initialize(serverLevel);
        }
    }

    /**
     * Initializes the subscription and adds it to the event loop.
     */
    protected void initialize(ServerLevel level) {
        TaskHandler.enqueueTask(level, this::updateSubscription, 0);
    }

    /**
     * Updates the subscription according to whether it should currently be active.
     */
    public void updateSubscription() {
        if (condition.getAsBoolean()) {
            subscription = handler.subscribeServerTick(subscription, runnable, cycle);
        } else if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    /**
     * Unsubscribes the subscription from the event loop.
     */
    public void unsubscribe() {
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
    }

    public void setCycle(int cycle) {
        this.cycle = cycle;
        if (subscription != null) subscription.cycle = cycle;
    }
}
