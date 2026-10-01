package com.gregtechceu.gtceu.api.machine.issue;

import com.gregtechceu.gtceu.uipro.flow.FlowState;

public enum DiagnosisState {

    OK,
    INSUFFICIENT,
    BLOCKED,
    VOIDED,
    MISSING_HANDLER,
    SKIPPED,
    LIMITED;

    private static final DiagnosisState[] VALUES = values();

    public static DiagnosisState of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : SKIPPED;
    }

    public IssueSeverity severity() {
        return switch (this) {
            case OK, SKIPPED -> IssueSeverity.INFO;
            case VOIDED, LIMITED -> IssueSeverity.WARNING;
            default -> IssueSeverity.BLOCKING;
        };
    }

    public boolean isProblem() {
        return severity() == IssueSeverity.BLOCKING;
    }

    public boolean isWarning() {
        return severity() == IssueSeverity.WARNING;
    }

    public FlowState flowState() {
        return switch (this) {
            case OK -> FlowState.READY;
            case VOIDED, LIMITED -> FlowState.WARNING;
            case SKIPPED -> FlowState.IDLE;
            default -> FlowState.MISSING;
        };
    }
}
