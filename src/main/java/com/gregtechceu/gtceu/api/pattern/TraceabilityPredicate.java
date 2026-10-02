package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.predicates.PredicateAbilities;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
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
    @Nullable
    private volatile CandidateItems candidateItems;

    public TraceabilityPredicate() {
        common = new ArrayList<>();
        limited = new ArrayList<>();
    }

    public TraceabilityPredicate(Predicate<MultiblockState> predicate, Supplier<BlockInfo> blockInfo, @Nullable Supplier<Block[]> candidates) {
        this();
        SimplePredicate simplePredicate = new SimplePredicate(predicate, blockInfo, candidates);
        common.add(simplePredicate);
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
        detachShared();
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
        detachShared();
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
        detachShared();
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
        detachShared();
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
        detachShared();
        common.forEach(predicate -> predicate.previewCount = count);
        limited.forEach(predicate -> predicate.previewCount = count);
        return this;
    }

    /**
     * Set renderMask.
     */
    public TraceabilityPredicate disableRenderFormed() {
        detachShared();
        common.forEach(predicate -> predicate.disableRenderFormed = true);
        limited.forEach(predicate -> predicate.disableRenderFormed = true);
        return this;
    }

    private static boolean isShared(@Nullable SimplePredicate predicate, SimplePredicate shared) {
        return predicate != null && predicate.is(shared);
    }

    private void detachShared() {
        detachShared(common);
        detachShared(limited);
    }

    private void detachShared(List<SimplePredicate> predicates) {
        for (int i = 0, size = predicates.size(); i < size; i++) {
            var predicate = predicates.get(i);
            if (predicate != SimplePredicate.ANY && predicate != SimplePredicate.AIR) continue;
            var copy = predicate.copyShared();
            predicates.set(i, copy);
        }
    }

    public TraceabilityPredicate excluding(PartAbility... abilities) {
        var result = new TraceabilityPredicate(this);
        result.exclude(result.common, abilities);
        result.exclude(result.limited, abilities);
        return result;
    }

    private void exclude(List<SimplePredicate> predicates, PartAbility[] abilities) {
        for (int i = 0; i < predicates.size(); i++) {
            if (!(predicates.get(i) instanceof PredicateAbilities ability)) continue;
            var copy = ability.excluding(abilities);
            predicates.set(i, copy);
        }
    }

    public void forEachSimple(Consumer<SimplePredicate> action) {
        for (int i = 0, size = common.size(); i < size; i++) action.accept(common.get(i));
        for (int i = 0, size = limited.size(); i < size; i++) action.accept(limited.get(i));
    }

    public ReferenceSet<Item> candidateItems() {
        int count = common.size() + limited.size();
        var cached = candidateItems;
        if (cached != null && cached.count == count) return cached.items;
        var items = new ReferenceLinkedOpenHashSet<Item>();
        addCandidates(common, items);
        addCandidates(limited, items);
        var result = ReferenceSets.unmodifiable(items);
        candidateItems = new CandidateItems(count, result);
        return result;
    }

    private static void addCandidates(List<SimplePredicate> predicates, ReferenceLinkedOpenHashSet<Item> items) {
        for (int i = 0, size = predicates.size(); i < size; i++) {
            var simple = predicates.get(i);
            if (simple == null || simple.candidates == null) continue;
            for (var block : simple.candidates.get()) {
                var item = SimplePredicate.toItem(block);
                if (item != Items.AIR) items.add(item);
            }
        }
    }

    private record CandidateItems(int count, ReferenceSet<Item> items) {}

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
        return this.common.size() == 1 && this.limited.isEmpty() && isShared(this.common.getFirst(), SimplePredicate.ANY);
    }

    public boolean isAir() {
        return this.common.size() == 1 && this.limited.isEmpty() && isShared(this.common.getFirst(), SimplePredicate.AIR);
    }

    public boolean isSingle() {
        return this.common.size() + this.limited.size() == 1;
    }

    public boolean hasAir() {
        for (int i = 0, size = common.size(); i < size; i++) {
            if (isShared(common.get(i), SimplePredicate.AIR)) return true;
        }
        return false;
    }
}
