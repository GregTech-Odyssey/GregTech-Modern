package com.gregtechceu.gtceu.api.machine.issue;

public enum DiagnosisTarget {

    NONE,
    RUNNING,
    LOCKED,
    REPORTED,
    MACHINE,
    LAST;

    private static final DiagnosisTarget[] VALUES = values();

    public static DiagnosisTarget of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
    }
}
