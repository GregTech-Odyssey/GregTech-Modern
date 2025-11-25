package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class TraceabilityPredicate {

    public static final TraceabilityPredicate AIR = new TraceabilityPredicate(SimplePredicate.AIR) {

        @Override
        public boolean test(MultiblockState worldState) {
            return worldState.getBlockState().isAir();
        }

        @Override
        public boolean isAny() {
            return false;
        }

        @Override
        public boolean isAir() {
            return true;
        }

        @Override
        public boolean isSingle() {
            return false;
        }

        @Override
        public boolean hasAir() {
            return true;
        }
    };

    public List<SimplePredicate> common = new ObjectArrayList<>();
    public List<SimplePredicate> limited = new ObjectArrayList<>();
    public Function<MultiblockState, Direction> direction = GTUtil.NULL_FUNCTION;
    public boolean isController;

    public TraceabilityPredicate() {}

    public TraceabilityPredicate(TraceabilityPredicate predicate) {
        common.addAll(predicate.common);
        limited.addAll(predicate.limited);
        isController = predicate.isController;
        direction = predicate.direction;
    }

    public TraceabilityPredicate(Predicate<MultiblockState> predicate, Supplier<BlockInfo> blockInfo, @Nullable Supplier<Block[]> candidates) {
        common.add(new SimplePredicate(predicate, blockInfo, candidates));
    }

    public TraceabilityPredicate(SimplePredicate simplePredicate) {
        if (simplePredicate.minCount != -1 || simplePredicate.maxCount != -1) {
            limited.add(simplePredicate);
        } else {
            common.add(simplePredicate);
        }
    }

    /**
     * Mark it as the controller of this multi. Normally you won't call it yourself. Use plz.
     */
    public TraceabilityPredicate setController() {
        isController = true;
        return this;
    }

    public TraceabilityPredicate sort() {
        limited.sort(Comparator.comparingInt(a -> a.minCount));
        if (common.isEmpty()) {
            common = Collections.emptyList();
        } else {
            common = ImmutableList.copyOf(common);
        }
        if (limited.isEmpty()) {
            limited = Collections.emptyList();
        } else {
            limited = ImmutableList.copyOf(limited);
        }
        return this;
    }

    /**
     * Add tooltips for candidates. They are shown in JEI Pages.
     */
    public TraceabilityPredicate addTooltips(Component... tips) {
        if (tips.length > 0) {
            List<Component> tooltips = Arrays.stream(tips).toList();
            common.forEach(predicate -> {
                if (predicate.candidates == null) return;
                if (predicate.toolTips == null) {
                    predicate.toolTips = new ObjectArrayList<>();
                }
                predicate.toolTips.addAll(tooltips);
            });
            limited.forEach(predicate -> {
                if (predicate.candidates == null) return;
                if (predicate.toolTips == null) {
                    predicate.toolTips = new ObjectArrayList<>();
                }
                predicate.toolTips.addAll(tooltips);
            });
        }
        return this;
    }

    /**
     * Set the minimum number of candidate blocks.
     */
    public TraceabilityPredicate setMinGlobalLimited(int min) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.minCount = min;
        }
        return this;
    }

    public TraceabilityPredicate setMinGlobalLimited(int min, int previewCount) {
        return this.setMinGlobalLimited(min).setPreviewCount(previewCount);
    }

    /**
     * Set the maximum number of candidate blocks.
     */
    public TraceabilityPredicate setMaxGlobalLimited(int max) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.maxCount = max;
        }
        return this;
    }

    public TraceabilityPredicate setMaxGlobalLimited(int max, int previewCount) {
        return this.setMaxGlobalLimited(max).setPreviewCount(previewCount);
    }

    /**
     * Set the minimum number of candidate blocks for each aisle layer.
     */
    public TraceabilityPredicate setMinLayerLimited(int min) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.minLayerCount = min;
        }
        return this;
    }

    public TraceabilityPredicate setMinLayerLimited(int min, int previewCount) {
        return this.setMinLayerLimited(min).setPreviewCount(previewCount);
    }

    /**
     * Set the maximum number of candidate blocks for each aisle layer.
     */
    public TraceabilityPredicate setMaxLayerLimited(int max) {
        limited.addAll(common);
        common.clear();
        for (SimplePredicate predicate : limited) {
            predicate.maxLayerCount = max;
        }
        return this;
    }

    public TraceabilityPredicate setMaxLayerLimited(int max, int previewCount) {
        return this.setMaxLayerLimited(max).setPreviewCount(previewCount);
    }

    /**
     * Sets the Minimum and Maximum limit to the passed value
     * 
     * @param limit The Maximum and Minimum limit
     */
    public TraceabilityPredicate setExactLimit(int limit) {
        return this.setMinGlobalLimited(limit).setMaxGlobalLimited(limit);
    }

    /**
     * Set the number of it appears in JEI pages. It only affects JEI preview. (The specific number)
     */
    public TraceabilityPredicate setPreviewCount(int count) {
        common.forEach(predicate -> predicate.previewCount = count);
        limited.forEach(predicate -> predicate.previewCount = count);
        return this;
    }

    /**
     * Set renderMask.
     */
    public TraceabilityPredicate disableRenderFormed() {
        common.forEach(predicate -> predicate.disableRenderFormed = true);
        limited.forEach(predicate -> predicate.disableRenderFormed = true);
        return this;
    }

    public boolean test(MultiblockState blockWorldState) {
        boolean flag = false;

        for (SimplePredicate predicate : limited) {
            if (predicate.testLimited(blockWorldState)) {
                flag = true;
            }
        }

        if (!flag) {
            for (SimplePredicate predicate : common) {
                if (predicate.test(blockWorldState)) {
                    flag = true;
                    break;
                }
            }
        }

        if (flag) {
            blockWorldState.setError(null);
        }
        return flag;
    }

    public TraceabilityPredicate or(TraceabilityPredicate other) {
        if (other != null) {
            TraceabilityPredicate newPredicate = new TraceabilityPredicate(this);
            newPredicate.common.addAll(other.common);
            newPredicate.limited.addAll(other.limited);
            return newPredicate;
        }
        return this;
    }

    public boolean testOnly() {
        return false;
    }

    public boolean isAny() {
        return this.common.size() == 1 && this.limited.isEmpty() && this.common.get(0) == SimplePredicate.ANY;
    }

    public boolean isAir() {
        return this.common.size() == 1 && this.limited.isEmpty() && this.common.get(0) == SimplePredicate.AIR;
    }

    public boolean isSingle() {
        return this.common.size() + this.limited.size() == 1;
    }

    public boolean hasAir() {
        return this.common.contains(SimplePredicate.AIR);
    }
}
