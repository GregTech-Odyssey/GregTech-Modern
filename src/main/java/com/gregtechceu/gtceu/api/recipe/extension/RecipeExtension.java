package com.gregtechceu.gtceu.api.recipe.extension;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ui.IRecipeInfo;

import com.fast.recipesearch.IntLongMap;
import com.gto.datasynclib.DataSyncCodec;
import com.gto.datasynclib.datastream.DataComponentKey;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("BooleanMethodIsAlwaysInverted")
public abstract class RecipeExtension<T> extends DataComponentKey<T> implements IRecipeInfo {

    public final boolean isTick;

    public RecipeExtension(String name, DataSyncCodec<T> codec, boolean isTick) {
        super(name, codec);
        this.isTick = isTick;
    }

    @SuppressWarnings("unused")
    public boolean handleInput(@NotNull IRecipeHandlerHolder holder, @NotNull RecipeHandlerUnit unit, @NotNull GTRecipe recipe, boolean simulate) {
        return true;
    }

    @SuppressWarnings("unused")
    public boolean handleOutput(@NotNull IRecipeHandlerHolder holder, @NotNull GTRecipe recipe, boolean simulate) {
        return true;
    }

    public boolean handleTick(@NotNull IRecipeHandlerHolder holder,
                              @NotNull GTRecipe recipe, boolean simulate) {
        return true;
    }

    public abstract void extractInput(GTRecipeDefinition recipe, IntLongMap map);

    public abstract long getParallel(IRecipeHandlerHolder holder, RecipeHandlerUnit unit,
                                     GTRecipe recipe, long parallel);

    public abstract void setParallel(GTRecipe recipe, long parallel);
}
