package com.gregtechceu.gtceu.api.pattern.predicates;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.List;

public class PredicateBlocks extends SimplePredicate {

    protected Block[] blocks = new Block[0];

    public PredicateBlocks() {
        super("blocks");
    }

    public PredicateBlocks(Block... blocks) {
        this();
        this.blocks = blocks;
        buildPredicate();
    }

    @Override
    public SimplePredicate buildPredicate() {
        List<Block> filteredBlocks = new ObjectArrayList<>(blocks.length);
        for (Block block : blocks) {
            if (block != null && block != Blocks.AIR) {
                filteredBlocks.add(block);
            }
        }
        if (filteredBlocks.isEmpty()) {
            throw new IllegalArgumentException("Empty predicate: " + Arrays.toString(blocks));
        }
        blocks = filteredBlocks.toArray(new Block[0]);
        blockInfo = () -> BlockInfo.fromBlock(blocks[0]);
        predicate = state -> ArrayUtils.contains(blocks, state.getBlockState().getBlock());
        candidates = () -> blocks;
        return this;
    }
}
