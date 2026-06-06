package com.gregtechceu.gtceu.api.recipe.extension;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import com.fast.recipesearch.IntLongMap;
import com.gto.datasynclib.DataSyncCodec;
import com.gto.datasynclib.datasream.DataComponentKey;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class RecipeExtension<T> extends DataComponentKey<T> {

    public final boolean isTick;

    public RecipeExtension(String name, DataSyncCodec<T> codec, boolean isTick) {
        super(name, codec);
        this.isTick = isTick;
    }

    public abstract boolean handle(IO io, @NotNull IRecipeHandlerHolder holder,
                                   @Nullable RecipeHandlerUnit unit,
                                   @NotNull GTRecipe recipe, boolean simulate);

    public abstract void extractInput(GTRecipeDefinition recipe, IntLongMap map);

    public abstract long getParallel(IRecipeHandlerHolder holder, RecipeHandlerUnit unit,
                                     GTRecipe recipe, long parallel);

    public abstract void setParallel(GTRecipe recipe, long parallel);

    public abstract void addInfo(GTRecipeDefinition recipe, WidgetGroup group,
                                 int xOffset, MutableInt yOffset);

    public abstract int getInfoHeight(GTRecipeDefinition recipe);
}
