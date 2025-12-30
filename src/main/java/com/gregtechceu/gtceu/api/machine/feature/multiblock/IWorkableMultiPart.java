package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import java.util.List;

public interface IWorkableMultiPart extends IMultiPart {

    boolean hasOnWorkingMethod();

    boolean hasBeforeWorkingMethod();

    boolean hasAfterWorkingMethod();

    boolean hasModifyRecipeMethod();

    /**
     * Get all available traits for recipe logic.
     */
    List<RecipeHandlerList> getRecipeHandlers();

    /**
     * Called per tick in {@link RecipeLogic#handleRecipeWorking()}
     */
    default boolean onWorking(IWorkableMultiController controller) {
        return true;
    }

    /**
     * Called per tick in {@link RecipeLogic#handleRecipeWorking()}
     */
    default void onWaiting(IWorkableMultiController controller) {}

    /**
     * Called in {@link WorkableMultiblockMachine#setWorkingEnabled(boolean)}
     */
    default void onPaused(IWorkableMultiController controller) {}

    /**
     * Called in {@link RecipeLogic#onRecipeFinish()} before outputs are produced
     */
    default void afterWorking(IWorkableMultiController controller) {}

    /**
     * Called in {@link RecipeLogic#setupRecipe(GTRecipe)}
     */
    default boolean beforeWorking(IWorkableMultiController controller, GTRecipe recipe) {
        return true;
    }

    /**
     * Override it to modify recipe on the fly e.g. applying overclock, change chance, etc
     *
     * @param recipe recipe from detected from GTRecipeType
     * @return modified recipe.
     *         null -- this recipe is unavailable
     */
    default GTRecipe modifyRecipe(IWorkableMultiController controller, GTRecipe recipe) {
        return recipe;
    }
}
