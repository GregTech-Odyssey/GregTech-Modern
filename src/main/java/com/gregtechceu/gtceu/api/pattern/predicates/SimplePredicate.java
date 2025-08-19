package com.gregtechceu.gtceu.api.pattern.predicates;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.error.SinglePredicateError;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SimplePredicate {

    public static SimplePredicate ANY = new SimplePredicate("any", GTUtil.FAVORABLE, null, null);
    public static SimplePredicate AIR = new SimplePredicate("air", blockWorldState -> blockWorldState.getBlockState().isAir(), null, null);

    @Nullable
    public Supplier<Block[]> candidates;
    public Supplier<BlockInfo> blockInfo;
    public Predicate<MultiblockState> predicate;
    public List<Component> toolTips;
    public int minCount = -1;
    public int maxCount = -1;
    public int minLayerCount = -1;
    public int maxLayerCount = -1;
    public int previewCount = -1;
    public boolean disableRenderFormed = false;

    public final String type;

    public SimplePredicate() {
        this("unknown");
    }

    public SimplePredicate(String type) {
        this.type = type;
    }

    public SimplePredicate(Predicate<MultiblockState> predicate, Supplier<BlockInfo> blockInfo, @Nullable Supplier<Block[]> candidates) {
        this();
        this.predicate = predicate;
        this.blockInfo = blockInfo == null ? GTUtil.NULL_SUPPLIER : blockInfo;
        this.candidates = candidates;
    }

    public SimplePredicate(String type, Predicate<MultiblockState> predicate, Supplier<BlockInfo> blockInfo,
                           @Nullable Supplier<Block[]> candidates) {
        this(type);
        this.predicate = predicate;
        this.blockInfo = blockInfo == null ? GTUtil.NULL_SUPPLIER : blockInfo;
        this.candidates = candidates;
    }

    public SimplePredicate buildPredicate() {
        return this;
    }

    @OnlyIn(Dist.CLIENT)
    public List<Component> getToolTips(TraceabilityPredicate predicates) {
        List<Component> result = new ArrayList<>();
        if (toolTips != null) {
            result.addAll(toolTips);
        }
        if (minCount == maxCount && maxCount != -1) {
            result.add(Component.translatable("gtceu.multiblock.pattern.error.limited_exact", minCount));
        } else if (minCount != maxCount && minCount != -1 && maxCount != -1) {
            result.add(Component.translatable("gtceu.multiblock.pattern.error.limited_within", minCount, maxCount));
        } else {
            if (minCount != -1) {
                result.add(LangHandler.getFromMultiLang("gtceu.multiblock.pattern.error.limited", 1, minCount));
            }
            if (maxCount != -1) {
                result.add(LangHandler.getFromMultiLang("gtceu.multiblock.pattern.error.limited", 0, maxCount));
            }
        }
        if (predicates == null) return result;
        if (predicates.isSingle()) {
            result.add(Component.translatable("gtceu.multiblock.pattern.single"));
        }
        if (predicates.hasAir()) {
            result.add(Component.translatable("gtceu.multiblock.pattern.replaceable_air"));
        }
        return result;
    }

    public boolean test(MultiblockState blockWorldState) {
        if (predicate.test(blockWorldState)) {
            return checkInnerConditions(blockWorldState);
        }
        return false;
    }

    public boolean testLimited(MultiblockState blockWorldState) {
        if (testGlobal(blockWorldState) && testLayer(blockWorldState)) {
            return checkInnerConditions(blockWorldState);
        }
        return false;
    }

    private boolean checkInnerConditions(MultiblockState blockWorldState) {
        if (disableRenderFormed) {
            blockWorldState.getMatchContext().getOrCreate("renderMask", LongOpenHashSet::new)
                    .add(blockWorldState.getPos().asLong());
        }
        return true;
    }

    public boolean testGlobal(MultiblockState blockWorldState) {
        if (minCount == -1 && maxCount == -1) return true;
        boolean base = predicate.test(blockWorldState);
        int count = blockWorldState.getGlobalCount().mergeInt(this, base ? 1 : 0, Integer::sum);
        if (maxCount == -1 || count <= maxCount) return base;
        blockWorldState.setError(new SinglePredicateError(this, 0));
        return false;
    }

    public boolean testLayer(MultiblockState blockWorldState) {
        if (minLayerCount == -1 && maxLayerCount == -1) return true;
        boolean base = predicate.test(blockWorldState);
        int count = blockWorldState.getLayerCount().mergeInt(this, base ? 1 : 0, Integer::sum);
        if (maxLayerCount == -1 || count <= maxLayerCount) return base;
        blockWorldState.setError(new SinglePredicateError(this, 2));
        return false;
    }

    public List<ItemStack> getCandidates() {
        return candidates == null ? Collections.emptyList() : Arrays.stream(this.candidates.get()).map(SimplePredicate::toItem).filter(i -> i != Items.AIR).map(Item::getDefaultInstance).toList();
    }

    public static Item toItem(Block block) {
        if (block instanceof LiquidBlock liquidBlock) {
            return liquidBlock.getFluid().getBucket();
        } else {
            return block.asItem();
        }
    }
}
