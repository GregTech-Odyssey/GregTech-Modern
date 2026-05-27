package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

public class AdjacentBlockCondition extends RecipeCondition {

    public final Block A;
    public final Block B;

    public AdjacentBlockCondition(boolean isReverse, Block a, Block b) {
        super(isReverse);
        A = a;
        B = b;
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.adjacent_block.tooltip");
    }

    @Override
    public boolean testCondition(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        boolean hasBlockA = false;
        boolean hasBlockB = false;
        for (Direction side : GTUtil.DIRECTIONS) {
            if (side.getAxis() != Direction.Axis.Y) {
                var block = holder.self().getNeighborBlockState(side).getBlock();
                if (block == A) {
                    hasBlockA = true;
                } else if (block == B) {
                    hasBlockB = true;
                }
                if (hasBlockA && hasBlockB) return true;
            }
        }
        return false;
    }
}
