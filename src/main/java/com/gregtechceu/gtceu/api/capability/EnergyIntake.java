package com.gregtechceu.gtceu.api.capability;

import com.gregtechceu.gtceu.utils.GTMath;

/**
 * 接收端每 tick 的输入安数额度：同一 tick 内所有来源共用一份，按来电电压折算成毫安累计。
 */
public final class EnergyIntake {

    private static final long MILLI = 1000;

    private int tick = Integer.MIN_VALUE;
    private long usedMilliAmps;

    public long limit(int now, long voltage, long amperage, long energy) {
        if (now != tick) {
            tick = now;
            usedMilliAmps = 0;
        }
        if (amperage >= Long.MAX_VALUE / MILLI) return energy;
        long left = amperage * MILLI - usedMilliAmps;
        if (left <= 0 || voltage <= 0) return 0;
        return Math.min(energy, GTMath.mulDivFloor(left, voltage, MILLI));
    }

    public void consume(long voltage, long energy) {
        if (energy > 0) usedMilliAmps += GTMath.mulDivCeil(energy, MILLI, voltage);
    }
}
