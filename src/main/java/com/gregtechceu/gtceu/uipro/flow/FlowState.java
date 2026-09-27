package com.gregtechceu.gtceu.uipro.flow;

import com.gregtechceu.gtceu.uipro.canvas.CanvasPulse;
import com.gregtechceu.gtceu.uipro.canvas.WireStyle;
import com.gregtechceu.gtceu.uipro.elements.StatusLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

public enum FlowState {

    DISABLED(UITheme.FLOW_WIRE_DISABLED, 0, 0, UITheme.FLOW_NODE_OUTLINE, StatusLine.Level.NORMAL),
    IDLE(UITheme.FLOW_WIRE_IDLE, UITheme.FLOW_OFF_LIGHT, UITheme.FLOW_OFF_MID, UITheme.FLOW_NODE_OUTLINE, StatusLine.Level.NORMAL),
    READY(UITheme.FLOW_WIRE_READY, UITheme.FLOW_GREEN_LIGHT, UITheme.FLOW_GREEN_MID, UITheme.FLOW_NODE_OUTLINE, StatusLine.Level.GOOD),
    ACTIVE(UITheme.FLOW_WIRE_ACTIVE, UITheme.FLOW_CYAN_LIGHT, UITheme.FLOW_CYAN_MID, UITheme.FLOW_NODE_OUTLINE, StatusLine.Level.GOOD),
    WARNING(UITheme.FLOW_WIRE_WARNING, UITheme.FLOW_AMBER_LIGHT, UITheme.FLOW_AMBER_MID, UITheme.FLOW_AMBER_DARK, StatusLine.Level.WARNING),
    MISSING(UITheme.FLOW_WIRE_MISSING, UITheme.FLOW_RED_LIGHT, UITheme.FLOW_RED_MID, UITheme.FLOW_RED_DARK, StatusLine.Level.ERROR);

    private static final FlowState[] VALUES = values();

    private final WireStyle wire;
    private final int stripLight;
    private final int stripMid;
    private final int outline;
    private final StatusLine.Level level;

    FlowState(WireStyle wire, int stripLight, int stripMid, int outline, StatusLine.Level level) {
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

    public WireStyle wire() {
        return wire;
    }

    public int stripLight() {
        return this == ACTIVE ? CanvasPulse.breathe(stripLight, UITheme.FLOW_CYAN_BRIGHT) : stripLight;
    }

    public int stripMid() {
        return stripMid;
    }

    public int outline() {
        return outline;
    }

    public StatusLine.Level level() {
        return level;
    }
}
