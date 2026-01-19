package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.NotNull;

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
    public boolean testCondition(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic) {
        boolean hasBlockA = false;
        boolean hasBlockB = false;
        for (Direction side : GTUtil.DIRECTIONS) {
            if (side.getAxis() != Direction.Axis.Y) {
                var block = recipeLogic.machine.self().getNeighborBlockState(side).getBlock();
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
