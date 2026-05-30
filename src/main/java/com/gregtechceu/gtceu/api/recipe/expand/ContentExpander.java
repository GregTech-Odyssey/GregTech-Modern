package com.gregtechceu.gtceu.api.recipe.expand;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import com.fast.recipesearch.IntLongMap;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ContentExpander {

    boolean isTick();

    boolean handle(IO io, @NotNull IRecipeHandlerHolder holder, @Nullable RecipeHandlerUnit unit, @NotNull GTRecipe recipe, boolean simulate);

    void extractInput(GTRecipeDefinition recipe, IntLongMap map);

    long getParallel(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long parallel);

    void setParallel(GTRecipe recipe, long parallel);

    void addInfo(GTRecipeDefinition recipe, WidgetGroup group, int xOffset, MutableInt yOffset);

    int getInfoHeight(GTRecipeDefinition recipe);
}
