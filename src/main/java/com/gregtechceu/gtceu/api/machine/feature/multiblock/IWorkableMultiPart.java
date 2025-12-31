package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.WorkableMultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.utils.asm.EmptyMethodChecker;

import java.util.List;

public interface IWorkableMultiPart extends IMultiPart {

    static boolean hasOnWorkingMethod(IWorkableMultiPart part) {
        var c = part.getClass();
        return WorkableMultiblockPartMachine.ON_WORKING_METHOD.computeIfAbsent(c, k -> {
            try {
                return EmptyMethodChecker.hasMethodBody(c.getMethod("onWorking", IWorkableMultiController.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    static boolean hasBeforeWorkingMethod(IWorkableMultiPart part) {
        var c = part.getClass();
        return WorkableMultiblockPartMachine.BEFORE_WORKING_METHOD.computeIfAbsent(c, k -> {
            try {
                return EmptyMethodChecker.hasMethodBody(c.getMethod("beforeWorking", IWorkableMultiController.class, GTRecipe.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    static boolean hasAfterWorkingMethod(IWorkableMultiPart part) {
        var c = part.getClass();
        return WorkableMultiblockPartMachine.AFTER_WORKING_METHOD.computeIfAbsent(c, k -> {
            try {
                return EmptyMethodChecker.hasMethodBody(c.getMethod("afterWorking", IWorkableMultiController.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    static boolean hasModifyRecipeMethod(IWorkableMultiPart part) {
        var c = part.getClass();
        return WorkableMultiblockPartMachine.MODIFY_RECIPE_METHOD.computeIfAbsent(c, k -> {
            try {
                return EmptyMethodChecker.hasMethodBody(c.getMethod("modifyRecipe", IWorkableMultiController.class, GTRecipe.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    default boolean hasOnWorkingMethod() {
        return IWorkableMultiPart.hasOnWorkingMethod(this);
    }

    default boolean hasBeforeWorkingMethod() {
        return IWorkableMultiPart.hasBeforeWorkingMethod(this);
    }

    default boolean hasAfterWorkingMethod() {
        return IWorkableMultiPart.hasAfterWorkingMethod(this);
    }

    default boolean hasModifyRecipeMethod() {
        return IWorkableMultiPart.hasModifyRecipeMethod(this);
    }

    default List<RecipeHandlerList> getRecipeHandlers() {
        return List.of(getHandlerList());
    }

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

    RecipeHandlerList getHandlerList();
}
