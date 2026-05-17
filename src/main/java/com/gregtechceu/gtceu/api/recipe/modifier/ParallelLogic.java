package com.gregtechceu.gtceu.api.recipe.modifier;

import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class ParallelLogic {

    public static final long MAX_PARALLEL = 9007199254740991L;

    public static long getRemainingMaxParallelAmount(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe) {
        if (recipe.contentParallel > 0) {
            return recipe.contentParallel / recipe.parallels;
        }
        return recipe.contentParallel = getMaxContentParallelAmount(holder, unit, recipe);
    }

    @Nullable
    public static GTRecipe accurateParallel(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long maxParallel) {
        if (maxParallel > 1) {
            long maxContentParallel = getMaxContentParallelAmount(holder, unit, recipe);
            if (maxContentParallel == 0) return null;
            recipe.contentParallel = maxContentParallel;
            maxParallel = Math.min(maxContentParallel, getTickParallelAmount(holder, unit, recipe, maxParallel));
            if (maxParallel == 0) return null;
            recipe.modifier(maxParallel, true);
            return recipe;
        }
        return recipe;
    }

    @Nullable
    public static GTRecipe accurateContentParallel(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long maxParallel) {
        if (maxParallel > 1) {
            long maxContentParallel = getMaxContentParallelAmount(holder, unit, recipe);
            if (maxContentParallel == 0) return null;
            recipe.contentParallel = maxContentParallel;
            maxParallel = Math.min(maxContentParallel, maxParallel);
            recipe.modifier(maxParallel, true);
            return recipe;
        }
        return recipe;
    }

    private static long getTickParallelAmount(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long maxParallel) {
        if (maxParallel > 1) {
            long eu = recipe.eut;
            if (eu != 0) {
                if (holder instanceof IOverclockMachine overclockMachine) {
                    if (eu < 0) {
                        eu = -eu;
                    }
                    maxParallel = Math.min(maxParallel, overclockMachine.getOverclockVoltage() / eu);
                }
            }
            for (var expand : recipe.definition.tickContentExpanders) {
                maxParallel = expand.getParallel(holder, unit, recipe, maxParallel);
                if (maxParallel == 0) return 0;
            }
        }
        return maxParallel;
    }

    public static long getMaxContentParallelAmount(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe) {
        var maxParallel = MAX_PARALLEL;
        var items = recipe.itemInputs;
        if (!items.isEmpty()) {
            maxParallel = unit.getInputItemParallelAmount(items, maxParallel);
            if (maxParallel == 0) return 0;
        }
        var fluids = recipe.fluidInputs;
        if (!fluids.isEmpty()) {
            maxParallel = unit.getInputFluidParallelAmount(fluids, maxParallel);
            if (maxParallel == 0) return 0;
        }
        for (var expand : recipe.definition.contentExpanders) {
            maxParallel = expand.getParallel(holder, unit, recipe, maxParallel);
            if (maxParallel == 0) return 0;
        }
        items = recipe.itemOutputs;
        if (!items.isEmpty()) {
            maxParallel = unit.getOutputItemParallelAmount(recipe, items, maxParallel);
            if (maxParallel == 0) return 0;
        }
        fluids = recipe.fluidOutputs;
        if (!fluids.isEmpty()) {
            maxParallel = unit.getOutputFluidParallelAmount(recipe, fluids, maxParallel);
        }
        return maxParallel;
    }
}
