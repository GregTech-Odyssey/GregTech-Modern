package com.gregtechceu.gtceu.common.pipelike.cable;

public final class PerTickLongCounter {

    private final long defaultValue;
    private final boolean override;
    private long lastUpdatedTime;
    private long currentValue;

    public PerTickLongCounter(boolean override) {
        this(0, override);
    }

    public PerTickLongCounter(long defaultValue, boolean override) {
        this.defaultValue = defaultValue;
        this.currentValue = defaultValue;
        this.override = override;
    }

    private void checkValueState(long time) {
        if (time != lastUpdatedTime) {
            this.lastUpdatedTime = time;
            this.currentValue = defaultValue;
        }
    }

    public long get(long time) {
        checkValueState(time);
        return currentValue;
    }

    public void set(long time, long value) {
        checkValueState(time);
        if (override) this.currentValue = value;
        else this.currentValue += value;
    }
}
