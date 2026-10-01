package com.gregtechceu.gtceu.api.machine.issue;

public enum IssueStage {

    SEARCH,
    TIER,
    CONDITION,
    MODIFIER,
    ENERGY,
    INPUT,
    OUTPUT,
    SETUP,
    WORKING,
    MACHINE;

    private static final IssueStage[] VALUES = values();

    public static IssueStage of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : SEARCH;
    }
}
