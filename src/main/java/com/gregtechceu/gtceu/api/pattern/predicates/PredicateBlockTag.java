package com.gregtechceu.gtceu.api.pattern.predicates;

import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Objects;

public class PredicateBlockTag extends SimplePredicate {

    protected final TagKey<Block> tag;

    public PredicateBlockTag(TagKey<Block> tag) {
        this.tag = tag;
        buildPredicate();
    }

    @Override
    public SimplePredicate buildPredicate() {
        if (tag == null) {
            predicate = GTUtil.NEGATIVE;
            blockInfo = () -> BlockInfo.EMPTY;
            candidates = () -> new Block[] { Blocks.AIR };
            return this;
        }
        predicate = state -> state.getBlockState().is(tag);
        var blocks = BuiltInRegistries.BLOCK.getTag(tag)
                .stream()
                .flatMap(HolderSet.Named::stream)
                .map(Holder::value)
                .toArray(Block[]::new);
        if (blocks.length == 0) blocks = new Block[] { Blocks.BARRIER };
        Block[] finalBlocks = blocks;
        candidates = () -> finalBlocks;
        var info = BlockInfo.fromBlock(blocks[0]);
        blockInfo = () -> info;
        return this;
    }

    @Override
    public int hashCode() {
        var hash = tag.hashCode();
        hash = 31 * hash + minCount;
        hash = 31 * hash + maxCount;
        hash = 31 * hash + minLayerCount;
        hash = 31 * hash + maxLayerCount;
        hash = 31 * hash + previewCount;
        hash = 31 * hash + (disableRenderFormed ? 1231 : 1237);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof PredicateBlockTag other)) return false;
        return Objects.equals(tag, other.tag) && minCount == other.minCount && maxCount == other.maxCount && minLayerCount == other.minLayerCount && maxLayerCount == other.maxLayerCount && previewCount == other.previewCount && disableRenderFormed == other.disableRenderFormed;
    }
}
