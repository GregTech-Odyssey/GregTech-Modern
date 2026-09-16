package com.gregtechceu.gtceu.api.recipe.ui;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;

import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;

import org.apache.commons.lang3.mutable.MutableInt;

public interface IRecipeInfo {

    /**
     * Simple method to get the tooltips for the recipe display.
     * Overrides {@link #addInfo(GTRecipeDefinition, WidgetGroup, int, MutableInt)} and
     * {@link #getInfoHeight(GTRecipeDefinition)}
     * if there is more than one component.
     * 
     * @return The tooltips for the recipe display.
     */
    default Component getTooltips() {
        return null;
    }

    /**
     * Adds information to the recipe display.
     * 
     * @param recipe  The recipe to display information for.
     * @param group   The widget group to add the information to.
     * @param xOffset The x offset of the recipe display.
     * @param yOffset The y offset of the recipe display.
     */
    default void addInfo(GTRecipeDefinition recipe, WidgetGroup group, int xOffset, MutableInt yOffset) {
        if (getTooltips() == null) return;
        group.addWidget(new LabelWidget(3 - xOffset, yOffset.addAndGet(10), getTooltips().getString()));
    }

    /**
     * Returns the height of the information to be displayed for the recipe.
     * Used to compute the total height of the recipe display.
     */
    default int getInfoHeight(GTRecipeDefinition recipe) {
        return getTooltips() == null ? 0 : 10;
    }
}
