package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.utils.asm.EmptyMethodChecker;

import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface IWorkableMultiPart extends IMultiPart {

    Reference2BooleanOpenHashMap<Class<?>> ON_WORKING_METHOD = new Reference2BooleanOpenHashMap<>();
    Reference2BooleanOpenHashMap<Class<?>> BEFORE_WORKING_METHOD = new Reference2BooleanOpenHashMap<>();
    Reference2BooleanOpenHashMap<Class<?>> AFTER_WORKING_METHOD = new Reference2BooleanOpenHashMap<>();
    Reference2BooleanOpenHashMap<Class<?>> MODIFY_RECIPE_METHOD = new Reference2BooleanOpenHashMap<>();

    static boolean hasOnWorkingMethod(IWorkableMultiPart part) {
        var c = part.getClass();
        return ON_WORKING_METHOD.computeIfAbsent(c, k -> {
            try {
                return EmptyMethodChecker.hasMethodBody(c.getMethod("onWorking", IWorkableMultiController.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    static boolean hasBeforeWorkingMethod(IWorkableMultiPart part) {
        var c = part.getClass();
        return BEFORE_WORKING_METHOD.computeIfAbsent(c, k -> {
            try {
                return EmptyMethodChecker.hasMethodBody(c.getMethod("beforeWorking", IWorkableMultiController.class, GTRecipe.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    static boolean hasAfterWorkingMethod(IWorkableMultiPart part) {
        var c = part.getClass();
        return AFTER_WORKING_METHOD.computeIfAbsent(c, k -> {
            try {
                return EmptyMethodChecker.hasMethodBody(c.getMethod("afterWorking", IWorkableMultiController.class));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        });
    }

    static boolean hasModifyRecipeMethod(IWorkableMultiPart part) {
        var c = part.getClass();
        return MODIFY_RECIPE_METHOD.computeIfAbsent(c, k -> {
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
        return Collections.singletonList(getHandlerList());
    }

    default RecipeHandlerList getHandlerList() {
        var list = getRecipeHandlerList();
        if (list == null) {
            List<IRecipeHandler<?>> handlers = new ArrayList<>();
            IO handlerIO = null;
            for (var trait : self().getTraits()) {
                if (trait instanceof IRecipeHandler<?> rht && rht.isAvailable()) {
                    if (handlerIO == null) handlerIO = rht.getHandlerIO();
                    handlers.add(rht);
                }
            }

            if (handlers.isEmpty()) {
                list = RecipeHandlerList.NO_DATA;
                setRecipeHandlerList(list);
            } else {
                list = RecipeHandlerList.of(handlerIO, this, handlers);
                setRecipeHandlerList(list);
            }
        }
        return list;
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

    default void afterWorking(IWorkableMultiController controller) {}

    /**
     * Called in {@link RecipeLogic#setupRecipe(GTRecipe)}
     */
    default boolean beforeWorking(IWorkableMultiController controller, @NotNull GTRecipe recipe) {
        return true;
    }

    /**
     * Override it to modify recipe on the fly e.g. applying overclock, change chance, etc
     *
     * @param recipe recipe from detected from GTRecipeType
     * @return modified recipe.
     *         null -- this recipe is unavailable
     */
    default @Nullable GTRecipe modifyRecipe(IWorkableMultiController controller, @NotNull GTRecipe recipe) {
        return recipe;
    }

    RecipeHandlerList getRecipeHandlerList();

    void setRecipeHandlerList(RecipeHandlerList list);
}
