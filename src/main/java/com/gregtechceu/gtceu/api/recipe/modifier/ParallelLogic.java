package com.gregtechceu.gtceu.api.recipe.modifier;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.feature.IVoidable;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.ActionResult;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;

import net.minecraft.MethodsReturnNonnullByDefault;

import java.util.List;

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
        return getMaxContentParallelAmount(holder, unit, recipe, MAX_PARALLEL);
    }

    public static long getMaxParallelAmount(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long maxParallel) {
        if (maxParallel > 1) {
            maxParallel = getMaxTickParallelAmount(holder, unit, recipe, maxParallel);
            if (maxParallel == 0) return 0;
            maxParallel = getMaxContentParallelAmount(holder, unit, recipe, maxParallel);
        }
        return maxParallel;
    }

    @Nullable
    public static GTRecipe accurateParallel(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long maxParallel) {
        if (maxParallel > 1) {
            maxParallel = getMaxParallelAmount(holder, unit, recipe, maxParallel);
            if (maxParallel == 0) return null;
            recipe.modifier(maxParallel, true);
            return recipe;
        }
        return recipe;
    }

    @Nullable
    public static GTRecipe accurateContentParallel(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long maxParallel) {
        if (maxParallel > 1) {
            maxParallel = getMaxContentParallelAmount(holder, unit, recipe, maxParallel);
            if (maxParallel == 0) return null;
            recipe.modifier(maxParallel, true);
            return recipe;
        }
        return recipe;
    }

    private static long getMaxTickParallelAmount(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long maxParallel) {
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

    public static long getMaxContentParallelAmount(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long maxParallel) {
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
        if (!(items.isEmpty() || (holder instanceof IVoidable voidable && voidable.canVoidRecipeOutputs(ItemRecipeCapability.CAP)))) {
            maxParallel = getOutputItemParallelAmount(holder.getOutputUnits(recipe), recipe, items, maxParallel);
            if (maxParallel == 0) {
                holder.setIdleReason(ActionResult.FAIL_INSUFFICIENT_OUT);
                return 0;
            }
        }
        fluids = recipe.fluidOutputs;
        if (!(fluids.isEmpty() || (holder instanceof IVoidable voidable && voidable.canVoidRecipeOutputs(FluidRecipeCapability.CAP)))) {
            maxParallel = getOutputFluidParallelAmount(holder.getOutputUnits(recipe), recipe, fluids, maxParallel);
            if (maxParallel == 0) {
                holder.setIdleReason(ActionResult.FAIL_INSUFFICIENT_OUT);
                return 0;
            }
        }
        recipe.contentParallel = maxParallel;
        return maxParallel;
    }

    public static long getOutputItemParallelAmount(List<RecipeHandlerUnit> list, GTRecipe recipe, List<Content<ItemIngredient>> contents, long multiplier) {
        for (var unit : list) {
            if (unit.isInfiniteOutputItem) return multiplier;
        }
        long minMultiplier = 0;
        long maxMultiplier = multiplier;
        long maxCount = 0;
        for (var content : contents) {
            maxCount = Math.max(maxCount, content.amount);
        }
        if (maxCount == 0) return multiplier;
        if (multiplier > ParallelLogic.MAX_PARALLEL / maxCount) {
            maxMultiplier = multiplier = ParallelLogic.MAX_PARALLEL / maxCount;
        }
        while (minMultiplier != maxMultiplier) {
            boolean success = false;
            var items = RecipeHelper.copyContents(contents, multiplier);
            for (var unit : list) {
                if (unit.handleRecipeItem(IO.OUT, recipe, items, true)) {
                    success = true;
                    break;
                }
            }
            if (!success && multiplier == 1) {
                return 0;
            }
            long[] bin = adjustMultiplier(success, minMultiplier, multiplier, maxMultiplier);
            minMultiplier = bin[0];
            multiplier = bin[1];
            maxMultiplier = bin[2];
        }
        return multiplier;
    }

    public static long getOutputFluidParallelAmount(List<RecipeHandlerUnit> list, GTRecipe recipe, List<Content<FluidIngredient>> contents, long multiplier) {
        for (var unit : list) {
            if (unit.isInfiniteOutputFluid) return multiplier;
        }
        long minMultiplier = 0;
        long maxMultiplier = multiplier;
        long maxCount = 0;
        for (var content : contents) {
            maxCount = Math.max(maxCount, content.amount);
        }
        if (maxCount == 0) return multiplier;
        if (multiplier > ParallelLogic.MAX_PARALLEL / maxCount) {
            maxMultiplier = multiplier = ParallelLogic.MAX_PARALLEL / maxCount;
        }
        while (minMultiplier != maxMultiplier) {
            boolean success = false;
            var fluids = RecipeHelper.copyContents(contents, multiplier);
            for (var unit : list) {
                if (unit.handleRecipeFluid(IO.OUT, recipe, fluids, true)) {
                    success = true;
                    break;
                }
            }
            if (!success && multiplier == 1) {
                return 0;
            }
            long[] bin = adjustMultiplier(success, minMultiplier, multiplier, maxMultiplier);
            minMultiplier = bin[0];
            multiplier = bin[1];
            maxMultiplier = bin[2];
        }
        return multiplier;
    }

    public static long[] adjustMultiplier(boolean mergedAll, long minMultiplier, long multiplier, long maxMultiplier) {
        if (mergedAll) {
            minMultiplier = multiplier;
            long remainder = (maxMultiplier - multiplier) % 2;
            multiplier = multiplier + remainder + (maxMultiplier - multiplier) / 2;
        } else {
            maxMultiplier = multiplier;
            multiplier = (multiplier + minMultiplier) / 2;
        }
        if (maxMultiplier - minMultiplier <= 1) {
            multiplier = maxMultiplier = minMultiplier;
        }
        return new long[] { minMultiplier, multiplier, maxMultiplier };
    }
}
