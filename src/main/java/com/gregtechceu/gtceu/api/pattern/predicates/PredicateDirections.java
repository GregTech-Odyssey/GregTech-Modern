package com.gregtechceu.gtceu.api.pattern.predicates;

import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;

import java.util.Arrays;

public class PredicateDirections extends SimplePredicate {

    protected final Block[] block;

    protected final RelativeDirection[] directions;

    public PredicateDirections(Block block, RelativeDirection... directions) {
        this.block = new Block[] { block };
        this.directions = directions;
        buildPredicate();
    }

    @Override
    public SimplePredicate buildPredicate() {
        predicate = blockWorldState -> {
            var state = blockWorldState.getBlockState();
            if (state.is(block[0])) {
                if (blockWorldState.controller == null) return true;
                var controller = blockWorldState.controller.self();
                if (state.hasProperty(DirectionalBlock.FACING)) {
                    for (var direction : directions) {
                        var relativeDirection = direction.getRelative(controller.getFrontFacing(), controller.getUpwardsFacing(), controller.isFlipped());
                        if (relativeDirection == state.getValue(DirectionalBlock.FACING)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        };
        var info = BlockInfo.fromBlockState(block[0].defaultBlockState().setValue(DirectionalBlock.FACING, directions[0].equivalentGlobal));
        blockInfo = () -> info;
        candidates = () -> block;
        return this;
    }

    @Override
    public int hashCode() {
        var hash = Arrays.hashCode(block);
        hash = 31 * hash + Arrays.hashCode(directions);;
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
        if (!(obj instanceof PredicateDirections other)) return false;
        return Arrays.equals(block, other.block) && Arrays.equals(directions, other.directions) && minCount == other.minCount && maxCount == other.maxCount && minLayerCount == other.minLayerCount && maxLayerCount == other.maxLayerCount && previewCount == other.previewCount && disableRenderFormed == other.disableRenderFormed;
    }
}
