package com.gregtechceu.gtceu.api.recipe.modifier;

import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.feature.IVoidable;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentInner;
import com.gregtechceu.gtceu.api.recipe.handler.ActionResult;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.info.ContentRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.EURecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.utils.OptimalSearch;

import net.minecraft.MethodsReturnNonnullByDefault;

import java.util.List;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 并行数计算：算出当前机器与分组一次能处理几份配方，并把它应用到配方上。
 *
 * <p>
 * 顺序是先「每 tick 的持续消耗」，再「内容库存」：
 * <ol>
 * <li>持续消耗——电压（{@code IOverclockMachine#getOverclockVoltage()} ÷ 单份电压）以及
 * tick 扩展（如算力）；</li>
 * <li>内容库存——输入的物品 / 流体库存、非 tick 扩展的需求，以及输出仓的剩余容量
 * （可虚空输出的机器跳过输出限制）。</li>
 * </ol>
 * 输入侧用「库存 ÷ 单份需求」取最小值，输出侧用 {@code OptimalSearch} 做精确搜索。
 *
 * <p>
 * {@link #accurateParallel} / {@link #accurateContentParallel} 在算完之后会调用
 * {@link GTRecipe#modifier} 把配方真的放大；算不出可用并行数（结果为 0）时返回 {@code null}，
 * 调用方据此判定这条配方当前不可用。
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class ParallelLogic {

    /** 并行数上限（{@code 2^53 - 1}），用于把倍率限制在 double 能精确表示的范围内。 */
    public static final long MAX_PARALLEL = 9007199254740991L;

    /**
     * 取「还能再放大几倍」。
     *
     * <p>
     * {@link GTRecipe#contentParallel} 是以单份配方为单位的内容容量缓存；
     * 已经放大了 {@link GTRecipe#parallels} 倍时，剩余倍数就是二者之商。
     * 缓存为空（{@code 0}）时重新计算一次内容上限。
     */
    public static long getRemainingMaxParallelAmount(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe) {
        if (recipe.contentParallel > 0) {
            return recipe.contentParallel / recipe.parallels;
        }
        return getMaxContentParallelAmount(holder, unit, recipe, MAX_PARALLEL);
    }

    /**
     * 依次受持续消耗与内容库存限制，取两者都允许的并行数。
     *
     * @param maxParallel 上游给定的上限；不大于 1 时不再计算，直接返回
     */
    public static long getMaxParallelAmount(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long maxParallel) {
        if (maxParallel > 1) {
            maxParallel = getMaxTickParallelAmount(holder, unit, recipe, maxParallel);
            if (maxParallel == 0) return 0;
            maxParallel = getMaxContentParallelAmount(holder, unit, recipe, maxParallel);
        }
        return maxParallel;
    }

    /**
     * 算出并行数并把配方放大到该倍率（会一并放大每 tick 的消耗）。
     *
     * @return 放大后的配方；算不出可用并行数时返回 {@code null}
     */
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

    /**
     * 只按内容库存算并行数并放大配方，不考虑每 tick 的消耗。
     *
     * <p>
     * 适用于持续消耗已经单独结算过的场合（例如批处理只需按内容份数合并）。
     *
     * @return 放大后的配方；算不出可用并行数时返回 {@code null}
     */
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

    /**
     * 每 tick 消耗带来的并行上限：电压不够就把停机原因写成「输入不足（EU）」，
     * 之后依次交给每个 tick 扩展（如算力）继续收紧。
     */
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

    /**
     * 内容库存带来的并行上限：先看输入（物品、流体、非 tick 扩展），再看输出容量。
     *
     * <p>
     * 输出侧只对非空、且机器不能虚空处理的那一类做检查，容量不够就把停机原因写成
     * {@link ActionResult#FAIL_INSUFFICIENT_OUT}。算出的上限会写进
     * {@link GTRecipe#contentParallel} 作为缓存。
     *
     * @return {@code min(maxParallel, 内容上限)}
     */
    public static long getMaxContentParallelAmount(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long maxParallel) {
        var parallel = MAX_PARALLEL;
        var items = recipe.itemInputs;
        if (!items.isEmpty()) {
            parallel = unit.getInputItemParallelAmount(items, parallel);
            if (parallel == 0) return 0;
        }
        var fluids = recipe.fluidInputs;
        if (!fluids.isEmpty()) {
            parallel = unit.getInputFluidParallelAmount(fluids, parallel);
            if (parallel == 0) return 0;
        }
        for (var extension : recipe.definition.recipeExtensions) {
            parallel = extension.getParallel(holder, unit, recipe, parallel);
            if (parallel == 0) return 0;
        }
        items = recipe.itemOutputs;
        if (!(items.isEmpty() || (holder instanceof IVoidable voidable && voidable.canVoidRecipeOutputs(ItemRecipeInfo.INSTANCE)))) {
            parallel = getOutputItemParallelAmount(holder.getOutputUnits(recipe), recipe, items, parallel);
            if (parallel == 0) {
                holder.setIdleReason(ActionResult.FAIL_INSUFFICIENT_OUT);
                return 0;
            }
        }
        fluids = recipe.fluidOutputs;
        if (!(fluids.isEmpty() || (holder instanceof IVoidable voidable && voidable.canVoidRecipeOutputs(FluidRecipeInfo.INSTANCE)))) {
            parallel = getOutputFluidParallelAmount(holder.getOutputUnits(recipe), recipe, fluids, parallel);
            if (parallel == 0) {
                holder.setIdleReason(ActionResult.FAIL_INSUFFICIENT_OUT);
                return 0;
            }
        }
        recipe.contentParallel = parallel;
        return Math.min(maxParallel, parallel);
    }

    /**
     * 输出物品的并行上限：用 {@code OptimalSearch} 精确搜索「多少个并行还能塞下」。
     *
     * <p>
     * 任一输出分组声明物品容量无限时直接返回上游给定的 {@code multiplier}，不必搜索。
     */
    public static long getOutputItemParallelAmount(List<RecipeHandlerUnit> list, GTRecipe recipe, List<Content<ItemIngredient>> contents, long multiplier) {
        for (var unit : list) {
            if (unit.isInfiniteItemCapacity) return multiplier;
        }
        long maxCount = 0;
        for (var content : contents) {
            maxCount = Math.max(maxCount, content.amount);
        }
        if (maxCount == 0) return multiplier;
        if (multiplier > ParallelLogic.MAX_PARALLEL / maxCount) {
            multiplier = ParallelLogic.MAX_PARALLEL / maxCount;
        }
        return OptimalSearch.exactSearch(multiplier, m -> {
            var copy = RecipeHelper.copyContents(contents, m);
            for (var unit : list) {
                if (unit.handleRecipeItem(IO.OUT, recipe, copy, true)) {
                    return true;
                }
            }
            return false;
        });
    }

    /** 输出流体的并行上限，语义同 {@link #getOutputItemParallelAmount}。 */
    public static long getOutputFluidParallelAmount(List<RecipeHandlerUnit> list, GTRecipe recipe, List<Content<FluidIngredient>> contents, long multiplier) {
        for (var unit : list) {
            if (unit.isInfiniteFluidCapacity) return multiplier;
        }
        long maxCount = 0;
        for (var content : contents) {
            maxCount = Math.max(maxCount, content.amount);
        }
        if (maxCount == 0) return multiplier;
        if (multiplier > ParallelLogic.MAX_PARALLEL / maxCount) {
            multiplier = ParallelLogic.MAX_PARALLEL / maxCount;
        }
        return OptimalSearch.exactSearch(multiplier, m -> {
            var copy = RecipeHelper.copyContents(contents, m);
            for (var unit : list) {
                if (unit.handleRecipeFluid(IO.OUT, recipe, copy, true)) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * {@link #getOutputItemParallelAmount} / {@link #getOutputFluidParallelAmount} 的泛型版本，
     * 可用来给其它内容种类计算输出并行上限。
     *
     * <p>
     * 与物品 / 流体版本不同，这里不做「容量无限」的短路，需要调用方自行判断。
     */
    public static <T, C extends ContentInner<T>> long getOutputContentParallelAmount(ContentRecipeInfo<T, C> key, List<RecipeHandlerUnit> list, GTRecipe recipe, List<Content<C>> contents, long multiplier) {
        long maxCount = 0;
        for (var content : contents) {
            maxCount = Math.max(maxCount, content.amount);
        }
        if (maxCount == 0) return multiplier;
        if (multiplier > ParallelLogic.MAX_PARALLEL / maxCount) {
            multiplier = ParallelLogic.MAX_PARALLEL / maxCount;
        }
        return OptimalSearch.exactSearch(multiplier, m -> {
            var copy = RecipeHelper.copyContents(contents, m);
            for (var unit : list) {
                if (unit.handleRecipeContent(key, IO.OUT, recipe, copy, true)) {
                    return true;
                }
            }
            return false;
        });
    }
}
