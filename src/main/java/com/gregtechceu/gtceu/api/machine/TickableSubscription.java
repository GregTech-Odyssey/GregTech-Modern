package com.gregtechceu.gtceu.api.machine;

public class TickableSubscription {

    protected final Runnable runnable;
    protected boolean stillSubscribed;

    public TickableSubscription(Runnable runnable) {
        this.runnable = runnable;
        this.stillSubscribed = true;
    }

    public void run() {
        runnable.run();
    }

    public void unsubscribe() {
        stillSubscribed = false;
    }

    public boolean isStillSubscribed() {
        return this.stillSubscribed;
    }
}
