package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;

import org.jetbrains.annotations.NotNull;

public class AdjacentFluidCondition extends RecipeCondition {

    public final Fluid A;
    public final Fluid B;

    public AdjacentFluidCondition(boolean isReverse, Fluid a, Fluid b) {
        super(isReverse);
        A = a;
        B = b;
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.rock_breaker.tooltip");
    }

    @Override
    public boolean testCondition(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic) {
        boolean hasFluidA = false;
        boolean hasFluidB = false;
        for (Direction side : GTUtil.DIRECTIONS) {
            if (side.getAxis() != Direction.Axis.Y) {
                var fluid = recipeLogic.machine.self().getNeighborFluidState(side).getType();
                if (fluid == A) {
                    hasFluidA = true;
                } else if (fluid == B) {
                    hasFluidB = true;
                }
                if (hasFluidA && hasFluidB) return true;
            }
        }
        return false;
    }
}
