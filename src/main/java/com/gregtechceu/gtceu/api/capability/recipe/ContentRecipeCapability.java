package com.gregtechceu.gtceu.api.capability.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentInner;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import com.fast.recipesearch.IntLongMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ContentRecipeCapability<T extends ContentInner> extends RecipeCapability<T> {

    protected ContentRecipeCapability(String name, int color, boolean doRenderSlot, int sortIndex) {
        super(name, color, doRenderSlot, sortIndex);
    }

    public abstract void convert(T content, IntLongMap map);

    @NotNull
    public abstract List<Object> createXEIContainerContents(List<Content<T>> contents, GTRecipeDefinition recipe, IO io);

    @Nullable
    public abstract Object createXEIContainer(List<?> contents);

    @Nullable("null when getWidgetClass() == null")
    public abstract Widget createWidget();

    /**
     * Return the class of the supported widget that should be used to display this capability.
     */
    @Nullable
    public abstract Class<? extends Widget> getWidgetClass();

    public abstract void applyWidgetInfo(@NotNull Widget widget,
                                         int index,
                                         boolean isXEI,
                                         IO io,
                                         @Nullable("null when storage == null") GTRecipeTypeUI.RecipeHolder recipeHolder,
                                         @NotNull GTRecipeType recipeType,
                                         @Nullable("null when content == null") GTRecipeDefinition recipe,
                                         @Nullable Content<T> content,
                                         @Nullable Object storage, int recipeTier, int chanceTier);
}
