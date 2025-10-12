package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.sound.SoundEntry;
import com.gregtechceu.gtceu.config.ConfigHolder;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A machine can handle recipes.
 */
public interface IRecipeLogicMachine extends IRecipeCapabilityHolder, IWorkable, ICleanroomReceiver,
                                     IVoidable {

    /**
     * RecipeType held
     */
    @NotNull
    GTRecipeType[] getRecipeTypes();

    @NotNull
    GTRecipeType getRecipeType();

    default GTRecipeType[] getCombinedTypes() {
        return getRecipeTypes();
    };

    default GTRecipeType[] getAvailableRecipeTypes() {
        return GTRecipeType.getAvailableTypes(this);
    }

    int getActiveRecipeType();

    void setActiveRecipeType(int type);

    default void setRecipeType(GTRecipeType type) {
        var types = getRecipeTypes();
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
     * Called when recipe logic status changed
     */
    default void notifyStatusChanged(RecipeLogic.Status oldStatus, RecipeLogic.Status newStatus) {
        self().requestSync();
    }

    /**
     * Recipe logic
     */
    @NotNull
    RecipeLogic getRecipeLogic();

    default RecipeLogic createRecipeLogic(Object... args) {
        return new RecipeLogic(this);
    }

    default GTRecipe fullModifyRecipe(GTRecipe recipe) {
        return doModifyRecipe(RecipeHelper.trimRecipeOutputs(recipe, this.getOutputLimits()));
    }

    /**
     * Override it to modify recipe on the fly e.g. applying overclock, change chance, etc
     *
     * @param recipe recipe from detected from GTRecipeType
     * @return modified recipe.
     *         null -- this recipe is unavailable
     */
    @Nullable
    default GTRecipe doModifyRecipe(GTRecipe recipe) {
        return self().getDefinition().getRecipeModifier().applyModifier(self(), recipe);
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
     * Called in {@link RecipeLogic#setupRecipe(GTRecipe)} ()}
     */
    default boolean beforeWorking(@Nullable GTRecipe recipe) {
        return true;
    }

    /**
     * Called per tick in {@link RecipeLogic#handleRecipeWorking()}
     */
    default boolean onWorking() {
        return self().getDefinition().getOnWorking().test(this);
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

    default boolean alwaysSearchRecipe() {
        return false;
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
