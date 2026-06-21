package com.gregtechceu.gtceu.api.pattern;

import net.minecraft.network.chat.Component;

import java.util.function.Predicate;

public record PatternCondition(Predicate<MultiblockState> condition, Component reason) {}
