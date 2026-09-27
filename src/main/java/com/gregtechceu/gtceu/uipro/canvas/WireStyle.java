package com.gregtechceu.gtceu.uipro.canvas;

public record WireStyle(int core, Pattern pattern, int glowLow, int glowHigh, int spark, int sparkTail) {

    public enum Pattern {
        SOLID,
        DASHED,
        DOTTED
    }

    public static WireStyle solid(int core) {
        return new WireStyle(core, Pattern.SOLID, 0, 0, 0, 0);
    }

    public static WireStyle patterned(int core, Pattern pattern) {
        return new WireStyle(core, pattern, 0, 0, 0, 0);
    }

    public static WireStyle flowing(int core, int glowLow, int glowHigh, int spark, int sparkTail) {
        return new WireStyle(core, Pattern.SOLID, glowLow, glowHigh, spark, sparkTail);
    }

    public boolean glows() {
        return glowLow != 0 || glowHigh != 0;
    }

    public boolean flows() {
        return spark != 0;
    }
}
