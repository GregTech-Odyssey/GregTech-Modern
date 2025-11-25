package com.gregtechceu.gtceu.api.pattern.predicates;

import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;

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
}
