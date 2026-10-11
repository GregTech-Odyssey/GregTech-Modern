package com.gregtechceu.gtceu.api.tooltip;

public final class TipTheme {

    public static final int HEADING = 0xFFAA00;
    public static final int BODY = 0xAAAAAA;
    public static final int VALUE = 0xFFFFFF;
    public static final int NOTE = 0x8C8C8C;
    public static final int LORE = 0x7E7E7E;
    public static final int MARK = 0x8C8C8C;
    public static final int MARK_DIM = 0x5E5E5E;
    public static final int MOUSE = 0xAAAAAA;
    public static final int ACCENT = 0xFFFF55;
    public static final int GOLD = 0xFFAA00;
    public static final int POSITIVE = 0x55FF55;
    public static final int NEGATIVE = 0xFF5555;
    public static final int ATTENTION = 0xFFFF55;

    public static final int ENERGY_VOLTAGE = 0x55FF55;
    public static final int ENERGY_AMPERAGE = 0xFFFF55;
    public static final int ENERGY_STORAGE = 0xFF5555;
    public static final int FLUID = 0x7A8CFF;
    public static final int ITEM = 0xFFAA00;
    public static final int PARALLEL = 0xFF55FF;
    public static final int TIER = 0x55FFFF;
    public static final int PRODUCTION = 0xFFFF55;
    public static final int EFFICIENCY = 0x55FFFF;

    public static final int ENERGY_BAR_SHADOW = 0xFF000000;
    public static final int ENERGY_BAR_BACKGROUND = 0xFF0E0116;
    public static final int ENERGY_BAR_TRACK = 0xFF3C3C3C;
    public static final int USAGE_WARN_LEFT = 0xFFB35A00;
    public static final int USAGE_WARN_RIGHT = 0xFFFFC266;
    public static final int DURABILITY_BAR_LEFT = 0xFF147C00;
    public static final int DURABILITY_BAR_RIGHT = 0xFF73FF59;
    public static final int ENERGY_BAR_LEFT = 0xFF0065B2;
    public static final int ENERGY_BAR_RIGHT = 0xFFD9EEFF;
    public static final int ENERGY_BAR_DEPLETED_LEFT = 0xFF7A0000;
    public static final int ENERGY_BAR_DEPLETED_RIGHT = 0xFFFF1B1B;

    private TipTheme() {}

    public static boolean energyDepleted(long charge, long max) {
        return max > 0 && charge * 4 <= max;
    }
}
