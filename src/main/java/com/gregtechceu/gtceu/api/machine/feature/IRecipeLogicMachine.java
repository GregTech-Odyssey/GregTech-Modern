package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.capability.ICleanroomReceiver;
import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.issue.GTIssues;
import com.gregtechceu.gtceu.api.machine.issue.IssueStage;
import com.gregtechceu.gtceu.api.machine.issue.IssueType;
import com.gregtechceu.gtceu.api.machine.issue.MachineIssue;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.sound.SoundEntry;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * A machine can handle recipes.
 */
public interface IRecipeLogicMachine extends IRecipeHandlerHolder, IWorkable, ICleanroomReceiver,
                                     IVoidable {

    static void attachRecipeLockConfigurator(ConfiguratorPanel configuratorPanel, IRecipeLogicMachine machine) {
        if (machine.supportLockRecipe() && !machine.alwaysSearchRecipe()) configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(WidgetIcons.RECIPE_LOCK_OFF, WidgetIcons.RECIPE_LOCK_ON, () -> machine.getRecipeLogic().isRecipeLocked(), (clickData, pressed) -> machine.getRecipeLogic().setRecipeLocked(pressed))
                .setTooltipsSupplier(pressed -> List.of(Component.translatable(pressed ? "gtceu.machine.recipe_lock_enabled" : "gtceu.machine.recipe_lock_disabled"))));
    }

    /**
     * definition
     */
    @NotNull
    default GTRecipeType[] getRecipeTypes() {
        return self().getDefinition().getRecipeTypes();
    }

    int getActiveRecipeType();

    void setActiveRecipeType(int type);

    /**
     * runtime
     */
    @Nullable
    GTRecipeType[] getAvailableRecipeTypesCache();

    void setAvailableRecipeTypesCache(@Nullable GTRecipeType[] types);

    default boolean recipeTypeAvailable(GTRecipeType type) {
        return true;
    }

    @NotNull
    default GTRecipeType[] getAvailableRecipeTypes() {
        var cache = getAvailableRecipeTypesCache();
        if (cache == null) {
            cache = getRecipeTypes();
            var list = new ArrayList<GTRecipeType>(cache.length);
            for (var type : cache) {
                if (recipeTypeAvailable(type)) list.add(type);
            }
            cache = list.toArray(new GTRecipeType[0]);
            setAvailableRecipeTypesCache(cache);
        }
        return cache;
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

    default boolean supportLockRecipe() {
        return true;
    }

    default RecipeLogic createRecipeLogic(Object... args) {
        return new RecipeLogic(this);
    }

    default GTRecipe fullModifyRecipe(RecipeHandlerUnit unit, GTRecipeDefinition definition) {
        if (!GTRecipeType.available(definition.recipeType, getAvailableRecipeTypes())) {
            reportIssue(GTIssues.NOT_APPLICABLE, null, IO.NONE, null, -1, 0, 0, definition);
            return null;
        }
        var recipe = definition.toRuntime();
        if (unit.color != -1) recipe.outputColor = unit.color;
        for (var mod : definition.recipeModifiers) {
            recipe = mod.applyModifier(this, unit, recipe);
            if (recipe == null) return null;
        }
        RecipeHelper.trimRecipeOutputs(recipe, getOutputLimits());
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
    @Deprecated
    default void setIdleReason(Supplier<Component> reason) {
        getRecipeLogic().reportCustom(reason);
    }

    @Override
    default void reportIssue(IssueType type, @Nullable IssueStage stage, IO io, @Nullable RecipeInfo capability, int index, long a, long b, @Nullable GTRecipeDefinition recipe) {
        getRecipeLogic().report(type, stage, io, capability, index, a, b, recipe);
    }

    @Nullable
    default GTRecipeDefinition getDiagnosisRecipe() {
        return null;
    }

    default long getIssueEnergyBuffer() {
        return 0;
    }

    default boolean isPowerGated() {
        return false;
    }

    default boolean hasDiagnosisTab() {
        return self() instanceof IMultiController;
    }

    default MachineIssue getUnavailableIssue() {
        return self() instanceof IMultiController controller && !controller.isFormed() ? GTIssues.UNFORMED.bare() : GTIssues.DISABLED.bare();
    }

    @Override
    default boolean matchRecipeOutput(GTRecipe recipe) {
        for (var e : recipe.definition.recipeExtensions) {
            if (!e.handleOutput(this, recipe, true)) {
                reportIssue(GTIssues.EXTENSION_UNMET, IssueStage.OUTPUT, IO.OUT, null, -1, 0, 0, recipe.definition);
                return false;
            }
        }
        List<Content<ItemIngredient>> items = canVoidRecipeOutputs(ItemRecipeInfo.INSTANCE) ? Collections.emptyList() : RecipeHelper.copyContents(recipe.itemOutputs, 1);
        List<Content<FluidIngredient>> fluids = canVoidRecipeOutputs(FluidRecipeInfo.INSTANCE) ? Collections.emptyList() : RecipeHelper.copyContents(recipe.fluidOutputs, 1);
        if (items.isEmpty() && fluids.isEmpty()) return true;
        var units = getOutputUnits(recipe);
        boolean itemAccepted = items.isEmpty();
        for (var handler : units) {
            if (!handler.handleRecipeItem(IO.OUT, recipe, items, true)) continue;
            itemAccepted = true;
            if (handler.handleRecipeFluid(IO.OUT, recipe, fluids, true)) return true;
        }
        reportOutputFailure(recipe, units.isEmpty(), itemAccepted);
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

    default boolean nextTickSearch() {
        return true;
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
