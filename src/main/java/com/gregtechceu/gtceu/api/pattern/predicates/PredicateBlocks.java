package com.gregtechceu.gtceu.api.pattern.predicates;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PredicateBlocks extends SimplePredicate {

    private static final Map<List<Block>, Candidates> CANDIDATES = new ConcurrentHashMap<>();

    protected Block[] blocks;
    private Candidates shared;

    public PredicateBlocks(Block... blocks) {
        this.blocks = blocks;
        buildPredicate();
    }

    public static PredicateBlocks of(Block... blocks) {
        return new PredicateBlocks(blocks);
    }

    @Override
    public SimplePredicate buildPredicate() {
        var filteredBlocks = new ReferenceLinkedOpenHashSet<Block>(blocks.length);
        for (Block block : blocks) {
            if (block != null && block != Blocks.AIR) {
                filteredBlocks.add(block);
            }
        }
        if (filteredBlocks.isEmpty()) {
            throw new IllegalArgumentException("Empty predicate: " + Arrays.toString(blocks));
        }
        var shared = CANDIDATES.computeIfAbsent(List.copyOf(filteredBlocks), Candidates::new);
        this.shared = shared;
        blocks = shared.blocks.clone();
        var block = blocks[0];
        if (block instanceof MetaMachineBlock) {
            blockInfo = () -> BlockInfo.fromBlock(block);
        } else {
            var info = BlockInfo.fromBlock(block);
            blockInfo = () -> info;
        }
        predicate = state -> shared.contains(state.getBlockState().getBlock());
        candidates = shared::copyBlocks;
        return this;
    }

    public boolean has(Block block) {
        return shared.contains(block);
    }

    private static final class Candidates {

        private final Block[] blocks;
        private final ReferenceOpenHashSet<Block> lookup;

        private Candidates(List<Block> blocks) {
            this.blocks = blocks.toArray(Block[]::new);
            this.lookup = new ReferenceOpenHashSet<>(this.blocks);
        }

        private boolean contains(Block block) {
            return lookup.contains(block);
        }

        private Block[] copyBlocks() {
            return blocks.clone();
        }
    }
}
