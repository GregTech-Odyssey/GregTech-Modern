package com.gregtechceu.gtceu.api.pattern.predicates;

import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class PredicateBlockTag extends SimplePredicate {

    protected TagKey<Block> tag = null;

    public PredicateBlockTag() {
        super("tags");
    }

    public PredicateBlockTag(TagKey<Block> tag) {
        this();
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
        candidates = () -> blocks;
        var info = BlockInfo.fromBlock(blocks[0]);
        blockInfo = () -> info;
        return this;
    }
}
