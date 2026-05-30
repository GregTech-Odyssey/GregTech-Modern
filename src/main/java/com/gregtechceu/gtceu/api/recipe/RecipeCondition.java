package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;

import org.apache.commons.lang3.mutable.MutableInt;

public abstract class RecipeCondition {

    protected final boolean isReverse;

    public RecipeCondition() {
        this(false);
    }

    public RecipeCondition(boolean isReverse) {
        this.isReverse = isReverse;
    }

    public boolean isOr() {
        return false;
    }

    public abstract Component getTooltips();

    public void addXEIInfo(GTRecipeDefinition recipe, WidgetGroup group, int xOffset, MutableInt yOffset) {
        if (getTooltips() == null) return;
        group.addWidget(new LabelWidget(3 - xOffset, yOffset.addAndGet(10), getTooltips().getString()));
    }

    public int getYOffset(GTRecipeDefinition recipe) {
        return 10;
    }

    public boolean check(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        boolean test = testCondition(holder, unit, recipe);
        return test != isReverse;
    }

    protected abstract boolean testCondition(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe);
}
