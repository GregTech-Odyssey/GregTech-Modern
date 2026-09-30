package com.gregtechceu.gtceu.api.pattern.predicates;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import appeng.hooks.IUnique;
import com.gto.fastcollection.cache.WeakValueCustomHashCache;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrays;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class PredicateBlocks extends SimplePredicate {

    private static final WeakValueCustomHashCache<Block[], Candidates> CANDIDATES = new WeakValueCustomHashCache<Block[], Candidates>(ObjectArrays.HASH_STRATEGY, Candidates::new);

    private final Candidates shared;

    public PredicateBlocks(Block... blocks) {
        this.shared = CANDIDATES.getCache(blocks);
        buildPredicate();
    }

    public static PredicateBlocks of(Block... blocks) {
        return new PredicateBlocks(blocks);
    }

    @Override
    public SimplePredicate buildPredicate() {
        this.candidates = shared;
        this.blockInfo = shared.blockInfo;
        this.predicate = shared;
        return this;
    }

    private static final class Candidates implements Predicate<MultiblockState>, Supplier<Block[]> {

        private final Block[] blocks;
        private final IntOpenHashSet lookup;
        private final Supplier<BlockInfo> blockInfo;

        private Candidates(Block[] blocks) {
            var filteredBlocks = new ReferenceLinkedOpenHashSet<Block>(blocks.length);
            for (Block block : blocks) {
                if (block != null && block != Blocks.AIR) {
                    filteredBlocks.add(block);
                }
            }
            if (filteredBlocks.isEmpty()) {
                throw new IllegalArgumentException("Empty predicate: " + Arrays.toString(blocks));
            }
            this.blocks = filteredBlocks.toArray(new Block[0]);
            var b = this.blocks[0];
            if (b instanceof MetaMachineBlock) {
                blockInfo = () -> BlockInfo.fromBlock(b);
            } else {
                var info = BlockInfo.fromBlock(b);
                blockInfo = () -> info;
            }
            this.lookup = new IntOpenHashSet(this.blocks.length);
            for (var block : this.blocks) {
                lookup.add(IUnique.getUid(block));
            }
        }

        @Override
        public boolean test(MultiblockState state) {
            return lookup.contains(IUnique.getUid(state.getBlockState().getBlock()));
        }

        @Override
        public Block[] get() {
            return blocks;
        }
    }
}
