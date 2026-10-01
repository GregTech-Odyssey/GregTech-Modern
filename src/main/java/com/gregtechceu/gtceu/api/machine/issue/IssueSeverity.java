package com.gregtechceu.gtceu.api.machine.issue;

import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.flow.FlowState;

public enum IssueSeverity {

    INFO,
    WARNING,
    BLOCKING;

    private static final IssueSeverity[] VALUES = values();

    public static IssueSeverity of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : INFO;
    }

    public FlowState flowState() {
        return switch (this) {
            case INFO -> FlowState.IDLE;
            case WARNING -> FlowState.WARNING;
            case BLOCKING -> FlowState.MISSING;
        };
    }

    public Level level() {
        return switch (this) {
            case INFO -> Level.NORMAL;
            case WARNING -> Level.WARNING;
            case BLOCKING -> Level.ERROR;
        };
    }
}
