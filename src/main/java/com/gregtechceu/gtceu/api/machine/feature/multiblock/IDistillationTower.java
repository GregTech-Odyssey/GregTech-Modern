package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.recipe.content.ContentRoll;
import com.gregtechceu.gtceu.api.recipe.handler.PlanScratch;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public interface IDistillationTower extends IWorkableMultiController {

    RecipeHandlerUnit VOID_LAYER = RecipeHandlerUnit.NO_DATA;

    List<RecipeHandlerUnit> getFluidOutputs();

    int getYOffset();

    default boolean addOutputs() {
        final int startY = self().getPos().getY() + getYOffset();
        List<IWorkableMultiPart> parts = Arrays.stream(getParts()).filter(IWorkableMultiPart.class::isInstance).map(IWorkableMultiPart.class::cast).filter(part -> PartAbility.EXPORT_FLUIDS.isApplicable(part.self().getBlockState().getBlock())).filter(part -> part.self().getPos().getY() >= startY).toList();
        if (!parts.isEmpty()) {
            // Loop from controller y + offset -> the highest output hatch
            int maxY = parts.getLast().self().getPos().getY();
            var fluidOutputs = getFluidOutputs();
            int outputIndex = 0;
            for (int y = startY; y <= maxY; ++y) {
                if (parts.size() <= outputIndex) {
                    fluidOutputs.add(VOID_LAYER);
                    continue;
                }
                var part = parts.get(outputIndex);
                if (part.self().getPos().getY() == y) {
                    var unit = part.getRecipeHandlers().getFirst();
                    fluidOutputs.add(unit.fluidHandlers.length == 0 ? VOID_LAYER : unit);
                    outputIndex++;
                } else if (part.self().getPos().getY() > y) {
                    fluidOutputs.add(VOID_LAYER);
                } else {
                    GTCEu.LOGGER.error("The Distillation Tower at {} has a fluid export hatch with an unexpected Y position", self().getPos());
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    default void beforeWorking(@NotNull RecipeHandlerUnit unit, @NotNull GTRecipe recipe) {
        updateWorkingRecipe(recipe);
        IWorkableMultiController.super.beforeWorking(unit, recipe);
    }

    @Override
    default boolean matchRecipeOutput(GTRecipe recipe) {
        var items = recipe.itemOutputs;
        var fluids = recipe.fluidOutputs;
        if (items.isEmpty() && fluids.isEmpty()) return true;
        boolean distillation = recipe.definition.recipeType == GTRecipeTypes.DISTILLATION_RECIPES;
        var p = PlanScratch.acquire();
        try {
            for (var unit : getOutputUnits(recipe)) {
                if (unit.fitsOutputs(recipe, p, recipe.scale, true, false)) {
                    if (fluids.isEmpty()) return true;
                    if (!distillation) {
                        if (unit.fitsOutputs(recipe, p, recipe.scale, false, true)) {
                            return true;
                        }
                    } else {
                        return applyFluidOutputs(recipe, null, true);
                    }
                }
            }
        } finally {
            PlanScratch.release();
        }
        return false;
    }

    @Override
    default boolean handleRecipeOutput(GTRecipe recipe) {
        var items = recipe.itemOutputs;
        var fluids = recipe.fluidOutputs;
        if (items.isEmpty() && fluids.isEmpty()) return true;
        long[] itemLeft = roll(recipe, items);
        long[] fluidLeft = roll(recipe, fluids);
        for (var handler : getOutputUnits(recipe)) {
            var item = handler.insertOutputs(items, itemLeft, false);
            if (fluids.isEmpty()) return item;
            if (recipe.definition.recipeType != GTRecipeTypes.DISTILLATION_RECIPES) {
                if (handler.insertOutputs(fluids, fluidLeft, true)) {
                    return item;
                }
            } else {
                return applyFluidOutputs(recipe, fluidLeft, false) && item;
            }
        }
        return false;
    }

    private static long[] roll(GTRecipe recipe, ContentList list) {
        int n = list.size();
        long[] amounts = new long[n];
        for (int i = 0; i < n; i++) {
            amounts[i] = ContentRoll.rolled(recipe, list, i, ContentRoll.RNG);
        }
        return amounts;
    }

    default boolean applyFluidOutputs(GTRecipe recipe, @Nullable long[] rolled, boolean simulate) {
        boolean valid = true;
        var fluids = recipe.fluidOutputs;
        var outputs = getFluidOutputs();
        int n = Math.min(fluids.size(), outputs.size());
        for (int i = 0; i < n; ++i) {
            long amount = rolled != null ? rolled[i] : fluids.chance(i) == 0 ? 0 : fluids.effective(i, recipe.scale);
            if (amount <= 0) continue;
            var unit = outputs.get(i);
            if (unit == VOID_LAYER) continue;
            if (!unit.output(fluids.outputKey(i), amount, simulate)) valid = false;
            if (simulate && !valid) break;
        }
        return valid;
    }

    default void updateWorkingRecipe(GTRecipe recipe) {
        if (recipe.definition.recipeType != GTRecipeTypes.DISTILLATION_RECIPES) return;
        var contents = recipe.fluidOutputs;
        if (contents.isEmpty()) return;
        var outputs = getFluidOutputs();
        var size = Math.min(contents.size(), outputs.size());
        if (size == 0) {
            recipe.fluidOutputs = ContentList.EMPTY;
        } else {
            var trimmed = contents.range(0, size);
            for (int i = 0; i < size; ++i) {
                if (outputs.get(i) == VOID_LAYER && trimmed.amount(i) != 0) {
                    trimmed = trimmed.withAmount(i, 0);
                }
            }
            recipe.fluidOutputs = trimmed;
        }
    }
}
