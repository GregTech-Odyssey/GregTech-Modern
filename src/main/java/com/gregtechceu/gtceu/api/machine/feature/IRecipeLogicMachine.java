package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.sound.SoundEntry;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    @NotNull
    GTRecipeType getRecipeType();

    default boolean disabledCombined() {
        return self().getDefinition().disabledCombined();
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
     * Recipe logic
     */
    @NotNull
    RecipeLogic getRecipeLogic();

    default RecipeLogic createRecipeLogic(Object... args) {
        return new RecipeLogic(this);
    }

    default GTRecipe fullModifyRecipe(RecipeHandlerUnit unit, GTRecipe recipe) {
        return doModifyRecipe(unit, RecipeHelper.trimRecipeOutputs(recipe, this.getOutputLimits()));
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

    default boolean checkConditions(RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        if (recipe.conditions.length == 0) return true;
        Map<Class<?>, List<RecipeCondition>> or = new Reference2ObjectArrayMap<>();
        for (RecipeCondition condition : recipe.conditions) {
            if (condition.isOr()) {
                or.computeIfAbsent(condition.getClass(), type -> new ArrayList<>()).add(condition);
            } else if (!condition.check(this, unit, recipe)) {
                return false;
            }
        }

        for (List<RecipeCondition> conditions : or.values()) {
            boolean passed = conditions.isEmpty();
            MutableComponent component = Component.translatable("gtceu.recipe_logic.condition_fails")
                    .append(": ");
            for (RecipeCondition condition : conditions) {
                passed = condition.check(this, unit, recipe);
                if (passed) break;
                else component.append(condition.getTooltips());
            }

            if (!passed) {
                return false;
            }
        }
        return true;
    }

    default boolean matchRecipe(RecipeHandlerUnit unit, GTRecipe recipe) {
        return matchRecipeInput(unit, recipe) && matchRecipeOutput(recipe);
    }

    default boolean matchRecipeInput(RecipeHandlerUnit unit, GTRecipe recipe) {
        if (unit.handleRecipeItem(IO.IN, recipe, recipe.itemInputs, true) && unit.handleRecipeFluid(IO.IN, recipe, recipe.fluidInputs, true)) {
            for (var e : recipe.definition.contentExpanders) {
                if (!e.handle(this, null, recipe, true)) return false;
            }
            return true;
        }
        return false;
    }

    default boolean matchRecipeOutput(GTRecipe recipe) {
        var items = GTRecipe.copyContents(recipe.itemOutputs, 1);
        var fluids = GTRecipe.copyContents(recipe.fluidOutputs, 1);
        for (var handler : getOutputList()) {
            if (handler.handleRecipeItem(IO.OUT, recipe, items, true) && handler.handleRecipeFluid(IO.OUT, recipe, fluids, true)) {
                return true;
            }
        }
        return false;
    }

    default boolean handleRecipeInput(RecipeHandlerUnit unit, GTRecipe recipe) {
        if (unit.handleRecipeItem(IO.IN, recipe, recipe.itemInputs, false) && unit.handleRecipeFluid(IO.IN, recipe, recipe.fluidInputs, false)) {
            for (var e : recipe.definition.contentExpanders) {
                if (!e.handle(this, null, recipe, false)) return false;
            }
            return true;
        }
        return false;
    }

    default boolean handleRecipeOutput(GTRecipe recipe) {
        var items = GTRecipe.copyContents(recipe.itemOutputs, 1);
        var fluids = GTRecipe.copyContents(recipe.fluidOutputs, 1);
        for (var handler : getOutputList()) {
            if (handler.handleRecipeItem(IO.OUT, recipe, items, false) && handler.handleRecipeFluid(IO.OUT, recipe, fluids, false)) {
                return true;
            }
        }
        return false;
    }

    default boolean matchTickRecipe(GTRecipe recipe) {
        var eu = recipe.eut;
        if (eu != 0) {
            return this instanceof IElectricMachine electricMachine && electricMachine.useEnergy(eu, true);
        }
        for (var e : recipe.definition.tickContentExpanders) {
            if (!e.handle(this, null, recipe, true)) return false;
        }
        return true;
    }

    default boolean handleTickRecipe(GTRecipe recipe) {
        var eu = recipe.eut;
        if (eu != 0) {
            return this instanceof IElectricMachine electricMachine && electricMachine.useEnergy(eu, false);
        }
        for (var e : recipe.definition.tickContentExpanders) {
            if (!e.handle(this, null, recipe, false)) return false;
        }
        return true;
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
    default void beforeWorking(@NotNull GTRecipe recipe) {}

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
