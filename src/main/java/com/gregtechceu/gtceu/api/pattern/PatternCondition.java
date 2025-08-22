package com.gregtechceu.gtceu.api.pattern;

import java.util.function.Predicate;

public record PatternCondition(Predicate<MultiblockState> condition, String translateKey) {}
