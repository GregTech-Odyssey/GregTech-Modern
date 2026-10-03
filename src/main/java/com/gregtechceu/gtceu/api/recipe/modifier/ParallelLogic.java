package com.gregtechceu.gtceu.api.recipe.modifier;

import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.feature.IVoidable;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.ActionResult;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.PlanScratch;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.info.EURecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

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
                    if (maxParallel == 0) {
                        holder.setIdleReason(() -> ActionResult.failInsufficientIn(EURecipeInfo.INSTANCE.getName()).reason());
                    }
                }
            }
            for (var extension : recipe.definition.tickRecipeExtensions) {
                maxParallel = extension.getParallel(holder, unit, recipe, maxParallel);
                if (maxParallel == 0) return 0;
            }
        }
        return maxParallel;
    }

    public static long getMaxContentParallelAmount(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long maxParallel) {
        long parallel = recipe.maxModifier();
        if (!recipe.itemInputs.isEmpty() || !recipe.fluidInputs.isEmpty()) {
            parallel = unit.inputParallel(recipe, parallel);
            if (parallel == 0) return 0;
        }
        for (var extension : recipe.definition.recipeExtensions) {
            parallel = extension.getParallel(holder, unit, recipe, parallel);
            if (parallel == 0) return 0;
        }
        boolean voidItems = holder instanceof IVoidable voidable && voidable.canVoidRecipeOutputs(ItemRecipeInfo.INSTANCE);
        boolean voidFluids = holder instanceof IVoidable voidable && voidable.canVoidRecipeOutputs(FluidRecipeInfo.INSTANCE);
        boolean needItems = !recipe.itemOutputs.isEmpty() && !voidItems;
        boolean needFluids = !recipe.fluidOutputs.isEmpty() && !voidFluids;
        if (needItems || needFluids) {
            parallel = getOutputParallelAmount(holder.getOutputUnits(recipe), recipe, parallel, needItems, needFluids);
            if (parallel == 0) {
                holder.setIdleReason(ActionResult.FAIL_INSUFFICIENT_OUT);
                return 0;
            }
        }
        recipe.contentParallel = parallel;
        return Math.min(maxParallel, parallel);
    }

    public static long getOutputParallelAmount(List<RecipeHandlerUnit> units, GTRecipe recipe, long limit, boolean items, boolean fluids) {
        if (limit <= 0) return 0;
        var p = PlanScratch.acquire();
        try {
            long best = 0;
            for (var unit : units) {
                boolean infinite = (!items || unit.isInfiniteItemCapacity || recipe.itemOutputs.isEmpty()) && (!fluids || unit.isInfiniteFluidCapacity || recipe.fluidOutputs.isEmpty());
                if (infinite) return limit;
                long bound = unit.outputParallelBound(recipe, p, items, fluids);
                long found;
                if (bound >= 0) {
                    found = bound < limit ? bound : limit;
                } else {
                    long high = ~bound < limit ? ~bound : limit;
                    found = high > best ? searchUnit(unit, recipe, p, best, high, items, fluids) : 0;
                }
                if (found > best) best = found;
                if (best == limit) break;
            }
            return best;
        } finally {
            PlanScratch.release();
        }
    }

    private static long searchUnit(RecipeHandlerUnit unit, GTRecipe recipe, PlanScratch p, long low, long high, boolean items, boolean fluids) {
        if (fits(unit, recipe, p, high, items, fluids)) return high;
        if (low > 0 && !fits(unit, recipe, p, low + 1, items, fluids)) return low;
        long lo = low, hi = high;
        while (lo + 1 < hi) {
            long mid = lo + (hi - lo) / 2;
            if (fits(unit, recipe, p, mid, items, fluids)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    private static boolean fits(RecipeHandlerUnit unit, GTRecipe recipe, PlanScratch p, long multiplier, boolean items, boolean fluids) {
        return unit.fitsOutputs(recipe, p, Keys.multiply(recipe.scale, multiplier), items, fluids);
    }
}
