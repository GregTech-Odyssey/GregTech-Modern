package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

import org.jetbrains.annotations.NotNull;

public class RockBreakerCondition extends RecipeCondition {

    public static final RockBreakerCondition INSTANCE = new RockBreakerCondition();

    private Fluid A;
    private Fluid B;

    public RockBreakerCondition(boolean isReverse) {
        super(isReverse);
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.rock_breaker.tooltip");
    }

    @Override
    public boolean testCondition(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic) {
        if (A == null || B == null) {
            A = BuiltInRegistries.FLUID.get(new ResourceLocation(recipe.data.getString("fluidA")));
            B = BuiltInRegistries.FLUID.get(new ResourceLocation(recipe.data.getString("fluidB")));
        }
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

    public RockBreakerCondition() {}
}
