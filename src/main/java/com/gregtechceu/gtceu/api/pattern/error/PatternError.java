package com.gregtechceu.gtceu.api.pattern.error;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;

import java.util.List;

public class PatternError {

    @Getter
    public BlockPos pos;
    public TraceabilityPredicate predicate;

    public void setWorldState(MultiblockState worldState) {
        pos = worldState.pos;
        predicate = worldState.predicate;
    }

    public List<List<ItemStack>> getCandidates() {
        List<List<ItemStack>> candidates = new ObjectArrayList<>();
        for (SimplePredicate common : predicate.common) {
            candidates.add(common.getCandidates());
        }
        for (SimplePredicate limited : predicate.limited) {
            candidates.add(limited.getCandidates());
        }
        return candidates;
    }

    public Component getErrorInfo() {
        List<List<ItemStack>> candidates = getCandidates();
        StringBuilder builder = new StringBuilder();
        for (List<ItemStack> candidate : candidates) {
            if (!candidate.isEmpty()) {
                if (!builder.isEmpty()) builder.append(", ");
                builder.append(candidate.getFirst().getDisplayName());
                if (candidate.size() > 1) builder.append("...");
            }
        }
        return Component.translatable("gtceu.multiblock.pattern.error", builder.toString(), pos.toShortString());
    }
}
