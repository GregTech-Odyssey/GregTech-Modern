package com.gregtechceu.gtceu.common.pipelike.cable;

public class PerTickLongCounter {

    private final long defaultValue;
    private long lastUpdatedTime;
    private long currentValue;

    public PerTickLongCounter() {
        this(0);
    }

    public PerTickLongCounter(long defaultValue) {
        this.defaultValue = defaultValue;
        this.currentValue = defaultValue;
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
        this.currentValue += value;
    }
}
