package com.gregtechceu.gtceu.uipro.render;

public final class UIStates {

    public static final int NONE = 0;
    public static final int HOVERED = 1;
    public static final int PRESSED = 1 << 1;
    public static final int SELECTED = 1 << 2;
    public static final int DISABLED = 1 << 3;
    public static final int FOCUSED = 1 << 4;
    public static final int CHECKED = 1 << 5;
    public static final int LATCHED = 1 << 6;

    private UIStates() {}

    public static boolean has(int states, int flags) {
        return (states & flags) == flags;
    }

    public static int with(int states, int flags, boolean on) {
        return on ? states | flags : states & ~flags;
    }
}
