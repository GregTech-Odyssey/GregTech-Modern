package com.gregtechceu.gtceu.api.machine;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

public class TickableSubscription implements ISubscription {

    protected final Runnable runnable;
    protected boolean stillSubscribed;

    public TickableSubscription(Runnable runnable) {
        this.runnable = runnable;
        this.stillSubscribed = true;
    }

    public void run() {
        runnable.run();
    }

    @Override
    public void unsubscribe() {
        stillSubscribed = false;
    }

    public boolean isStillSubscribed() {
        return this.stillSubscribed;
    }
}
