package com.gregtechceu.gtceu.uipro.flow;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public abstract class ThrottledStatus {

    private final IntSupplier clock;
    private final int interval;
    private boolean refreshed;
    private int refreshedAt;

    protected ThrottledStatus(IntSupplier clock, int interval) {
        this.clock = clock;
        this.interval = interval;
    }

    protected void onAccess(int now) {}

    protected abstract void refresh(int now);

    public final void update() {
        int now = clock.getAsInt();
        onAccess(now);
        if (refreshed && now >= refreshedAt && now - refreshedAt < interval) return;
        refreshed = true;
        refreshedAt = now;
        refresh(now);
    }

    public final <T> Supplier<T> live(Supplier<T> value) {
        return () -> {
            update();
            return value.get();
        };
    }
}
