package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import com.google.common.collect.ImmutableList;
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

    public final List<SimplePredicate> common;
    public final List<SimplePredicate> limited;
    public Function<MultiblockState, Direction> direction = GTUtil.NULL_FUNCTION;

    public TraceabilityPredicate() {
        common = new ArrayList<>();
        limited = new ArrayList<>();
    }

    public TraceabilityPredicate(Predicate<MultiblockState> predicate, Supplier<BlockInfo> blockInfo, @Nullable Supplier<Block[]> candidates) {
        this();
        common.add(new SimplePredicate(predicate, blockInfo, candidates));
    }

    public TraceabilityPredicate(SimplePredicate simplePredicate) {
        this();
        if (simplePredicate.minCount != -1 || simplePredicate.maxCount != -1) {
            limited.add(simplePredicate);
        } else {
            common.add(simplePredicate);
        }
    }

    protected TraceabilityPredicate(TraceabilityPredicate predicate) {
        this.common = new ArrayList<>(predicate.common);
        this.limited = new ArrayList<>(predicate.limited);
        this.direction = predicate.direction;
    }

    protected TraceabilityPredicate(TraceabilityPredicate predicate, Object ignored) {
        if (predicate.common.isEmpty()) {
            this.common = Collections.emptyList();
        } else {
            this.common = ImmutableList.copyOf(predicate.common);
        }
        var limited = new ArrayList<>(predicate.limited);
        limited.sort(Comparator.comparingInt(a -> a.minCount));
        if (limited.isEmpty()) {
            this.limited = Collections.emptyList();
        } else {
            this.limited = ImmutableList.copyOf(limited);
        }
        this.direction = predicate.direction;
    }

    public TraceabilityPredicate sort() {
        if (getClass() != TraceabilityPredicate.class) return this;
        return new TraceabilityPredicate(this, null);
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
                    predicate.toolTips = new ArrayList<>();
                }
                predicate.toolTips.addAll(tooltips);
            });
            limited.forEach(predicate -> {
                if (predicate.candidates == null) return;
                if (predicate.toolTips == null) {
                    predicate.toolTips = new ArrayList<>();
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
        return this.common.size() == 1 && this.limited.isEmpty() && this.common.getFirst() == SimplePredicate.ANY;
    }

    public boolean isAir() {
        return this.common.size() == 1 && this.limited.isEmpty() && this.common.getFirst() == SimplePredicate.AIR;
    }

    public boolean isSingle() {
        return this.common.size() + this.limited.size() == 1;
    }

    public boolean hasAir() {
        return this.common.contains(SimplePredicate.AIR);
    }
}
