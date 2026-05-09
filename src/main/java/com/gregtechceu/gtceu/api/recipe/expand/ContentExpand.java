package com.gregtechceu.gtceu.api.recipe.expand;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import com.fast.recipesearch.IntLongMap;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public abstract class ContentExpand {

    public final String name;
    public final int color;
    public final boolean doRenderSlot;
    public final int sortIndex;

    protected ContentExpand(String name, int color, boolean doRenderSlot, int sortIndex) {
        this.name = name;
        this.color = color;
        this.doRenderSlot = doRenderSlot;
        this.sortIndex = sortIndex;
    }

    public abstract boolean handle(@NotNull IRecipeHandlerHolder holder, RecipeHandlerUnit unit, @NotNull GTRecipe recipe, boolean simulate);

    public abstract void extractInput(GTRecipeDefinition recipe, IntLongMap map);

    public abstract long getParallel(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long parallel);

    public abstract void setParallel(GTRecipe recipe, long parallel);

    public String slotName(IO io) {
        return "%s_%s".formatted(name, io.name().toLowerCase(Locale.ROOT));
    }

    public String slotName(IO io, int index) {
        return "%s_%s_%s".formatted(name, io.name().toLowerCase(Locale.ROOT), index);
    }

    public MutableComponent getName() {
        return Component.translatable("recipe.capability.%s.name".formatted(name));
    }

    public MutableComponent getColoredName() {
        return getName().withStyle(style -> style.withColor(this.color));
    }

    public void addXEIInfo(GTRecipeDefinition recipe, WidgetGroup group, int xOffset, MutableInt yOffset, boolean isTick) {}
}
