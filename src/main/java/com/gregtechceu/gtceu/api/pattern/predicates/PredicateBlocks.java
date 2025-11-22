package com.gregtechceu.gtceu.api.pattern.predicates;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.List;

public class PredicateBlocks extends SimplePredicate {

    protected Block[] blocks;

    public PredicateBlocks(Block... blocks) {
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
        var block = blocks[0];
        if (block instanceof MetaMachineBlock) {
            blockInfo = () -> BlockInfo.fromBlock(block);
        } else {
            var info = BlockInfo.fromBlock(block);
            blockInfo = () -> info;
        }
        predicate = state -> ArrayUtils.contains(blocks, state.getBlockState().getBlock());
        candidates = () -> blocks;
        return this;
    }

    @Override
    public int hashCode() {
        var hash = Arrays.hashCode(blocks);
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
        if (!(obj instanceof PredicateBlocks other)) return false;
        return Arrays.equals(blocks, other.blocks) && minCount == other.minCount && maxCount == other.maxCount && minLayerCount == other.minLayerCount && maxLayerCount == other.maxLayerCount && previewCount == other.previewCount && disableRenderFormed == other.disableRenderFormed;
    }
}
