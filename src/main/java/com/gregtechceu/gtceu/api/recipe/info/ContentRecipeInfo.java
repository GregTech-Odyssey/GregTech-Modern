package com.gregtechceu.gtceu.api.recipe.info;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ContentRecipeInfo extends RecipeInfo {

    protected ContentRecipeInfo(String name, int color, boolean doRenderSlot, int sortIndex) {
        super(name, color, doRenderSlot, sortIndex);
    }

    @NotNull
    public abstract List<Object> createXEIContainerContents(ContentList contents, GTRecipeDefinition recipe, IO io);

    @Nullable
    public abstract Object createXEIContainer(List<?> contents);

    @Nullable("null when getWidgetClass() == null")
    public abstract Widget createWidget();

    @Nullable
    public abstract Class<? extends Widget> getWidgetClass();

    public abstract void applyWidgetInfo(@NotNull Widget widget,
                                         int index,
                                         boolean isXEI,
                                         IO io,
                                         @Nullable("null when storage == null") GTRecipeTypeUI.RecipeHolder recipeHolder,
                                         @NotNull GTRecipeType recipeType,
                                         @Nullable("null when content == null") GTRecipeDefinition recipe,
                                         @Nullable ContentList contents,
                                         int contentIndex,
                                         @Nullable Object storage, int recipeTier, int chanceTier);
}
