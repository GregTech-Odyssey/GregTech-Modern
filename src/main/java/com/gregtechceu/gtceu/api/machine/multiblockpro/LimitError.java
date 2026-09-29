package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

final class LimitError extends PatternError {

    private final SimplePredicate predicate;
    private final int max;

    LimitError(SimplePredicate predicate, int max) {
        this.predicate = predicate;
        this.max = max;
    }

    @Override
    public LimitError copy() {
        return new LimitError(predicate, max);
    }

    @Override
    public List<List<ItemStack>> getCandidates() {
        return Collections.singletonList(predicate.getCandidates());
    }

    @Override
    public Component getErrorInfo() {
        return Component.translatable("gtceu.multiblock.pattern.error.limited.0", max).append("-").append(pos.toShortString()).append("\n").append(super.getErrorInfo());
    }
}
