package com.gregtechceu.gtceu.api.pattern.error;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class PatternError {

    @Getter
    public BlockPos pos;
    public TraceabilityPredicate predicate;

    public PatternError copy() {
        return new PatternError();
    }

    public void setWorldState(MultiblockState worldState) {
        pos = worldState.pos;
        predicate = worldState.predicate;
    }

    public List<List<ItemStack>> getCandidates() {
        List<List<ItemStack>> candidates = new ArrayList<>();
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
        MutableComponent builder = Component.empty();
        boolean first = false;
        int count = 0;
        for (List<ItemStack> candidate : candidates) {
            if (!candidate.isEmpty()) {
                if (first) builder.append("\n");
                first = true;
                count++;
                builder.append(candidate.getFirst().getDisplayName());
                if (candidate.size() > 1) builder.append("...");
                if (count > 5) break;
            }
        }
        return Component.translatable("gtceu.multiblock.pattern.error", builder, pos.toShortString());
    }
}
