package com.gregtechceu.gtceu.uipro.flow;

import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.animation.UIClock;
import com.gregtechceu.gtceu.uipro.canvas.WireStyle;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

public enum FlowState {

    DISABLED(() -> UITheme.FLOW_WIRE_DISABLED, () -> 0, () -> 0, () -> UITheme.FLOW_NODE_OUTLINE, Level.NORMAL),
    IDLE(() -> UITheme.FLOW_WIRE_IDLE, () -> UITheme.FLOW_OFF_LIGHT, () -> UITheme.FLOW_OFF_MID, () -> UITheme.FLOW_NODE_OUTLINE, Level.NORMAL),
    READY(() -> UITheme.FLOW_WIRE_READY, () -> UITheme.FLOW_GREEN_LIGHT, () -> UITheme.FLOW_GREEN_MID, () -> UITheme.FLOW_NODE_OUTLINE, Level.GOOD),
    ACTIVE(() -> UITheme.FLOW_WIRE_ACTIVE, () -> UITheme.FLOW_CYAN_LIGHT, () -> UITheme.FLOW_CYAN_MID, () -> UITheme.FLOW_NODE_OUTLINE, Level.GOOD),
    WARNING(() -> UITheme.FLOW_WIRE_WARNING, () -> UITheme.FLOW_AMBER_LIGHT, () -> UITheme.FLOW_AMBER_MID, () -> UITheme.FLOW_AMBER_DARK, Level.WARNING),
    MISSING(() -> UITheme.FLOW_WIRE_MISSING, () -> UITheme.FLOW_RED_LIGHT, () -> UITheme.FLOW_RED_MID, () -> UITheme.FLOW_RED_DARK, Level.ERROR);

    private static final FlowState[] VALUES = values();

    private final Supplier<WireStyle> wire;
    private final IntSupplier stripLight;
    private final IntSupplier stripMid;
    private final IntSupplier outline;
    private final Level level;

    FlowState(Supplier<WireStyle> wire, IntSupplier stripLight, IntSupplier stripMid, IntSupplier outline, Level level) {
        this.wire = wire;
        this.stripLight = stripLight;
        this.stripMid = stripMid;
        this.outline = outline;
        this.level = level;
    }

    public static FlowState of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : IDLE;
    }

    public static FlowState worst(FlowState a, FlowState b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    public boolean isLit() {
        return this == ACTIVE || this == READY;
    }

    public WireStyle getWire() {
        return wire.get();
    }

    public int getStripLight() {
        return this == ACTIVE ? UIClock.breathe(stripLight.getAsInt(), UITheme.FLOW_CYAN_BRIGHT) : stripLight.getAsInt();
    }

    public int getStripMid() {
        return stripMid.getAsInt();
    }

    public int getOutline() {
        return outline.getAsInt();
    }

    public Level getLevel() {
        return level;
    }
}
