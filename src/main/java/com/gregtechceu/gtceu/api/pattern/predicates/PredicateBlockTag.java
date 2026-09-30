package com.gregtechceu.gtceu.api.pattern.predicates;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import com.gto.fastcollection.cache.WeakValueIdentityHashCache;

import java.util.function.Predicate;
import java.util.function.Supplier;

public class PredicateBlockTag extends SimplePredicate {

    private static final WeakValueIdentityHashCache<TagKey<Block>, Candidates> CANDIDATES = new WeakValueIdentityHashCache<>(Candidates::new);

    private final Candidates shared;

    public PredicateBlockTag(TagKey<Block> tag) {
        this.shared = CANDIDATES.getCache(tag);
        buildPredicate();
    }

    public static PredicateBlockTag of(TagKey<Block> tag) {
        return new PredicateBlockTag(tag);
    }

    @Override
    public SimplePredicate buildPredicate() {
        this.candidates = shared;
        this.blockInfo = shared.blockInfo;
        this.predicate = shared;
        return this;
    }

    private static final class Candidates implements Predicate<MultiblockState>, Supplier<Block[]> {

        private final TagKey<Block> tag;
        private final Block[] blocks;
        private final Supplier<BlockInfo> blockInfo;

        private Candidates(TagKey<Block> tagKey) {
            this.blocks = BuiltInRegistries.BLOCK.getTag(tagKey).map(holders -> holders.stream().map(Holder::value).toArray(Block[]::new)).orElseThrow();
            if (this.blocks.length > 0) {
                var b = this.blocks[0];
                if (b instanceof MetaMachineBlock) {
                    blockInfo = () -> BlockInfo.fromBlock(b);
                } else {
                    var info = BlockInfo.fromBlock(b);
                    blockInfo = () -> info;
                }
            } else {
                blockInfo = () -> BlockInfo.EMPTY;
            }
            tag = tagKey;
        }

        @Override
        public boolean test(MultiblockState state) {
            return state.getBlockState().is(tag);
        }

        @Override
        public Block[] get() {
            return blocks;
        }
    }
}
