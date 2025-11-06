package com.gregtechceu.gtceu.api.machine;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

public class TickableSubscription implements ISubscription {

    public final Runnable runnable;
    public boolean stillSubscribed;

    public int lastTick;
    public int cycle;

    public TickableSubscription(Runnable runnable) {
        this.runnable = runnable;
        this.stillSubscribed = true;
    }

    @Override
    public void unsubscribe() {
        stillSubscribed = false;
    }
}
