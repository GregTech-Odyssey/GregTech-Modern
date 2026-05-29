package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.VoidFluidHandler;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public interface IDistillationTower extends IWorkableMultiController {

    List<IFluidHandler> getFluidOutputs();

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
                    fluidOutputs.add(VoidFluidHandler.INSTANCE);
                    continue;
                }
                var part = parts.get(outputIndex);
                if (part.self().getPos().getY() == y) {
                    var handler = part.getRecipeHandlers().getFirst().getCapabilities(IFluidHandler.class).stream().findFirst().orElse(VoidFluidHandler.INSTANCE);
                    fluidOutputs.add(handler);
                    outputIndex++;
                } else if (part.self().getPos().getY() > y) {
                    fluidOutputs.add(VoidFluidHandler.INSTANCE);
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
        var items = RecipeHelper.copyContents(recipe.itemOutputs, 1);
        var fluids = RecipeHelper.copyContents(recipe.fluidOutputs, 1);
        if (items.isEmpty() && fluids.isEmpty()) return true;
        for (var handler : getOutputUnits(recipe)) {
            if (handler.handleRecipeItem(IO.OUT, recipe, items, true)) {
                if (fluids.isEmpty()) return true;
                if (recipe.definition.recipeType != GTRecipeTypes.DISTILLATION_RECIPES) {
                    if (handler.handleRecipeFluid(IO.OUT, recipe, fluids, true)) {
                        return true;
                    }
                } else {
                    return applyFluidOutputs(fluids, IFluidHandler.FluidAction.SIMULATE);
                }
            }
        }
        return false;
    }

    @Override
    default boolean handleRecipeOutput(GTRecipe recipe) {
        var items = RecipeHelper.copyAndRoll(recipe, recipe.itemOutputs);
        var fluids = RecipeHelper.copyAndRoll(recipe, recipe.fluidOutputs);
        if (items.isEmpty() && fluids.isEmpty()) return true;
        for (var handler : getOutputUnits(recipe)) {
            if (handler.handleRecipeItem(IO.OUT, recipe, items, false)) {
                if (fluids.isEmpty()) return true;
                if (recipe.definition.recipeType != GTRecipeTypes.DISTILLATION_RECIPES) {
                    if (handler.handleRecipeFluid(IO.OUT, recipe, fluids, false)) {
                        return true;
                    }
                } else {
                    return applyFluidOutputs(fluids, IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }
        return false;
    }

    default boolean applyFluidOutputs(List<Content<FluidIngredient>> fluids, IFluidHandler.FluidAction action) {
        boolean valid = true;
        var outputs = getFluidOutputs();
        for (int i = 0; i < Math.min(fluids.size(), outputs.size()); ++i) {
            var handler = outputs.get(i);
            var output = fluids.get(i);
            var fluid = output.inner.getFluidStack(output.getIntAmount());
            int filled = (handler instanceof NotifiableFluidTank nft) ? nft.fillInternal(fluid, action) : handler.fill(fluid, action);
            if (filled != fluid.getAmount()) valid = false;
            if (action.simulate() && !valid) break;
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
            recipe.fluidOutputs = Collections.emptyList();
        } else {
            var trimmed = new ArrayList<Content<FluidIngredient>>(size);
            for (int i = 0; i < size; ++i) {
                if ((outputs.get(i) instanceof VoidFluidHandler)) {
                    trimmed.add(Content.EMPTY_FLUID);
                } else {
                    trimmed.add(contents.get(i));
                }
            }
            recipe.fluidOutputs = trimmed;
        }
    }
}
