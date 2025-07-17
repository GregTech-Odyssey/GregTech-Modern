package com.gregtechceu.gtceu.api.machine;

public class TickableSubscription {

    private final Runnable runnable;
    private boolean stillSubscribed;

    public TickableSubscription(Runnable runnable) {
        this.runnable = runnable;
        this.stillSubscribed = true;
    }

    public void run() {
        if (stillSubscribed) {
            runnable.run();
        }
    }

    public void unsubscribe() {
        stillSubscribed = false;
    }

    public boolean isStillSubscribed() {
        return this.stillSubscribed;
    }
}
