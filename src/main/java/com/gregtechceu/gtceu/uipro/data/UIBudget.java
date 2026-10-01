package com.gregtechceu.gtceu.uipro.data;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

/**
 * 每个打开的界面一个令牌桶：所有客户端请求共用，按刻补充，防止用多个控件叠加绕过单个请求的限流。
 */
public final class UIBudget {

    public static final int CAPACITY = 64;
    public static final int REFILL_PER_TICK = 8;
    public static final int REQUEST_COST = 1;
    public static final int STRUCTURE_COST = 8;

    private long lastTick = Long.MIN_VALUE;
    private int tokens = CAPACITY;

    public static UIBudget of(ModularUI ui) {
        return ((Holder) (Object) ui).gtceu$budget();
    }

    public boolean tryConsume(long tick, int cost) {
        if (tick != lastTick) {
            long elapsed = lastTick == Long.MIN_VALUE ? CAPACITY : Math.max(0, tick - lastTick);
            tokens = (int) Math.min(CAPACITY, tokens + Math.min(elapsed, CAPACITY) * REFILL_PER_TICK);
            lastTick = tick;
        }
        if (tokens < cost) return false;
        tokens -= cost;
        return true;
    }

    public interface Holder {

        UIBudget gtceu$budget();
    }
}
