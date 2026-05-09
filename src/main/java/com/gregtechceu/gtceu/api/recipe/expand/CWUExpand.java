package com.gregtechceu.gtceu.api.recipe.expand;

import com.gregtechceu.gtceu.api.machine.feature.IComputationContainerMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import com.fast.recipesearch.IntLongMap;
import org.jetbrains.annotations.NotNull;

public final class CWUExpand extends ContentExpand {

    public static final CWUExpand INSTANCE = new CWUExpand();

    private CWUExpand() {
        super("cwu", 0xFFEEEE00, false, 3);
    }

    @Override
    public boolean handle(@NotNull IRecipeHandlerHolder holder, RecipeHandlerUnit unit, @NotNull GTRecipe recipe, boolean simulate) {
        var cwu = recipe.getInputCWUt();
        if (cwu < 1) return true;
        if (holder instanceof IComputationContainerMachine machine) {
            return machine.requestCWU(cwu, simulate) >= cwu;
        } else {
            return false;
        }
    }

    @Override
    public void extractInput(GTRecipeDefinition recipe, IntLongMap map) {}

    @Override
    public long getParallel(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long parallel) {
        var cwu = recipe.getInputCWUt();
        if (cwu < 1) return parallel;
        if (holder instanceof IComputationContainerMachine machine) {
            return Math.min(parallel, machine.getMaxCWU() / cwu);
        } else {
            return 0;
        }
    }

    @Override
    public void setParallel(GTRecipe recipe, long parallel) {
        var cwu = recipe.getInputCWUt();
        if (cwu < 1) return;
        recipe.setCWUt(cwu * parallel);
    }
}
