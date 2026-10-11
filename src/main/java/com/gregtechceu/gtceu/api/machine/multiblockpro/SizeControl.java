package com.gregtechceu.gtceu.api.machine.multiblockpro;

/**
 * 面板上一个派生尺寸控件：由一对相反方向的测量值换算出总尺寸或控制器位置，读写都落回原始测量值。
 * 总尺寸 = 低侧 + 高侧 + bias，位置 = 低侧测量值；居中控件两侧最多差一格，对称控件两侧相等。
 */
public final class SizeControl {

    public enum Kind {
        SPAN,
        POSITION,
        CENTERED,
        SYMMETRIC
    }

    private final ParamKey key;
    private final Kind kind;
    private final int low, high, bias, min, max;
    private final int lowMin, lowMax, highMin, highMax;

    SizeControl(ParamKey key, Kind kind, int low, int high, int bias, int min, int max, Structure.Option lowOption, Structure.Option highOption) {
        this.key = key;
        this.kind = kind;
        this.low = low;
        this.high = high;
        this.bias = bias;
        this.min = min;
        this.max = max;
        this.lowMin = lowOption.min();
        this.lowMax = lowOption.max();
        this.highMin = highOption.min();
        this.highMax = highOption.max();
    }

    public ParamKey key() {
        return key;
    }

    public Kind kind() {
        return kind;
    }

    int low() {
        return low;
    }

    int high() {
        return high;
    }

    boolean spans() {
        return kind != Kind.POSITION;
    }

    boolean accepts(int lowValue, int highValue) {
        int span = lowValue + highValue + bias;
        if (span < min || span > max) return false;
        return switch (kind) {
            case CENTERED -> Math.abs(lowValue - highValue) <= 1;
            case SYMMETRIC -> lowValue == highValue;
            default -> true;
        };
    }

    public int read(int[] values) {
        return kind == Kind.POSITION ? values[low] : values[low] + values[high] + bias;
    }

    public int min(int[] values) {
        if (kind != Kind.POSITION) return min;
        return Math.max(lowMin, values[low] + values[high] - highMax);
    }

    public int max(int[] values) {
        if (kind != Kind.POSITION) return max;
        return Math.min(lowMax, values[low] + values[high] - highMin);
    }

    public void write(int[] values, int value) {
        switch (kind) {
            case SPAN -> {
                int total = Math.max(min, Math.min(max, value)) - bias;
                int lowValue = Math.max(lowMin, Math.min(Math.min(lowMax, total - highMin), values[low]));
                lowValue = Math.max(lowValue, total - highMax);
                values[low] = lowValue;
                values[high] = total - lowValue;
            }
            case POSITION -> {
                int total = values[low] + values[high];
                int lowValue = Math.max(min(values), Math.min(max(values), value));
                values[low] = lowValue;
                values[high] = total - lowValue;
            }
            case CENTERED -> {
                int total = Math.max(min, Math.min(max, value)) - bias;
                values[low] = total / 2;
                values[high] = total - total / 2;
            }
            case SYMMETRIC -> {
                int total = value - bias;
                if ((total & 1) != 0) total += value > read(values) ? 1 : -1;
                int half = Math.max((min - bias + 1) / 2, Math.min((max - bias) / 2, total / 2));
                values[low] = half;
                values[high] = half;
            }
        }
    }
}
