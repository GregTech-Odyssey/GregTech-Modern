package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.ActionResult;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.sound.SoundEntry;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * A machine can handle recipes.
 */
public interface IRecipeLogicMachine extends IRecipeHandlerHolder, IWorkable, ICleanroomReceiver,
                                     IVoidable {

    /**
     * RecipeType held
     */
    @NotNull
    GTRecipeType[] getRecipeTypes();

    int getActiveRecipeType();

    void setActiveRecipeType(int type);

    @NotNull
    default GTRecipeType[] getAvailableRecipeTypes() {
        return getRecipeTypes();
    }

    @NotNull
    default GTRecipeType getRecipeType() {
        var types = getAvailableRecipeTypes();
        return types[Math.min(types.length - 1, getActiveRecipeType())];
    }

    default void setRecipeType(GTRecipeType type) {
        var types = getAvailableRecipeTypes();
        if (types.length > 1 && getRecipeType() != type) {
            int i = 0;
            for (var t : types) {
                if (t == type) {
                    setActiveRecipeType(i);
                    break;
                }
                i++;
            }
        }
    }

    /**
     * Recipe logic
     */
    @NotNull
    RecipeLogic getRecipeLogic();

    default RecipeLogic createRecipeLogic(Object... args) {
        return new RecipeLogic(this);
    }

    default GTRecipe fullModifyRecipe(RecipeHandlerUnit unit, GTRecipe recipe) {
        if (unit.color != -1) recipe.outputColor = unit.color;
        return doModifyRecipe(unit, recipe);
    }

    /**
     * Override it to modify recipe on the fly e.g. applying overclock, change chance, etc
     *
     * @param recipe recipe from detected from GTRecipeType
     * @return modified recipe.
     *         null -- this recipe is unavailable
     */
    @Nullable
    default GTRecipe doModifyRecipe(RecipeHandlerUnit unit, GTRecipe recipe) {
        return self().getDefinition().getRecipeModifier().applyModifier(this, unit, recipe);
    }

    @Override
    default void setIdleReason(Supplier<Component> reason) {
        getRecipeLogic().setIdleReasonSupplier(reason);
    }

    @Override
    default boolean matchRecipeOutput(GTRecipe recipe) {
        for (var e : recipe.definition.contentExpanders) {
            if (!e.handle(IO.OUT, this, null, recipe, true)) return false;
        }
        List<Content<ItemIngredient>> items = canVoidRecipeOutputs(ItemRecipeCapability.CAP) ? Collections.emptyList() : RecipeHelper.copyContents(recipe.itemOutputs, 1);
        List<Content<FluidIngredient>> fluids = canVoidRecipeOutputs(FluidRecipeCapability.CAP) ? Collections.emptyList() : RecipeHelper.copyContents(recipe.fluidOutputs, 1);
        if (items.isEmpty() && fluids.isEmpty()) return true;
        for (var handler : getOutputUnits(recipe)) {
            if (handler.handleRecipeItem(IO.OUT, recipe, items, true) && handler.handleRecipeFluid(IO.OUT, recipe, fluids, true)) {
                return true;
            }
        }
        setIdleReason(ActionResult.FAIL_INSUFFICIENT_OUT);
        return false;
    }

    /**
     * Whether the recipe logic should keep subscribing tick logic when no recipe is available after one cycle.
     * if false. you should call {@link RecipeLogic#updateTickSubscription()} manually later to active recipe logic
     * again.
     */
    default boolean keepSubscribing() {
        return false;
    }

    /**
     * Whether the recipe logic should work or waiting for next {@link RecipeLogic#updateTickSubscription()}.
     */
    default boolean isRecipeLogicAvailable() {
        return true;
    }

    /**
     * Called in {@link RecipeLogic#setupRecipe(RecipeHandlerUnit,GTRecipe)} ()
     */
    default void beforeWorking(@NotNull RecipeHandlerUnit unit, @NotNull GTRecipe recipe) {}

    /**
     * Called per tick in {@link RecipeLogic#handleRecipeWorking()}
     */
    default void onWorking() {
        self().getDefinition().getOnWorking().accept(this);
    }

    /**
     * Called per tick in {@link RecipeLogic#handleRecipeWorking()}
     */
    default void onWaiting() {}

    /**
     * Called in {@link RecipeLogic#onRecipeFinish()} before outputs are produced
     */
    default void afterWorking() {}

    default void onRecipeFinish() {}

    default void regressRecipe(RecipeLogic logic) {
        if (logic.progress > 0 && regressWhenWaiting()) {
            if (ConfigHolder.INSTANCE.machines.recipeProgressLowEnergy) {
                logic.progress = 1;
            } else {
                logic.progress = Math.max(1, logic.progress - 2);
            }
        }
    }

    default SoundEntry getSound() {
        return null;
    }

    /**
     * Whether progress decrease when machine is waiting for pertick ingredients. (e.g. lack of EU)
     */
    default boolean regressWhenWaiting() {
        return self().getDefinition().isRegressWhenWaiting();
    }

    default boolean shouldWorkingPlaySound() {
        return ConfigHolder.INSTANCE.machines.machineSounds &&
                (!(self() instanceof IMufflableMachine mufflableMachine) || !mufflableMachine.isMuffled());
    }

    //////////////////////////////////////
    // ******* IWorkable ********//
    //////////////////////////////////////
    @Override
    default boolean isWorkingEnabled() {
        return getRecipeLogic().isWorkingEnabled();
    }

    @Override
    default void setWorkingEnabled(boolean isWorkingAllowed) {
        getRecipeLogic().setWorkingEnabled(isWorkingAllowed);
    }

    @Override
    default void setSuspendAfterFinish(boolean suspendAfterFinish) {
        getRecipeLogic().setSuspendAfterFinish(suspendAfterFinish);
    }

    @Override
    default int getProgress() {
        return getRecipeLogic().getProgress();
    }

    @Override
    default int getMaxProgress() {
        return getRecipeLogic().getMaxProgress();
    }

    @Override
    default boolean isActive() {
        return getRecipeLogic().isActive();
    }
}
