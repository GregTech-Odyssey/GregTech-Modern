package com.gregtechceu.gtceu.api.capability.recipe;

import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceArrayMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface IRecipeCapabilityHolder {

    default boolean hasCapabilityProxies() {
        return !getCapabilitiesProxy().isEmpty();
    }

    @Nullable
    default RecipeHandlerList getCurrentHandlerList() {
        return null;
    }

    default void setCurrentHandlerList(RecipeHandlerList list) {}

    default List<RecipeHandlerList> getInputList() {
        return getCapabilitiesProxy().getOrDefault(IO.IN, Collections.emptyList());
    }

    default List<RecipeHandlerList> getOutputList() {
        return getCapabilitiesProxy().getOrDefault(IO.OUT, Collections.emptyList());
    }

    @NotNull
    Map<IO, List<RecipeHandlerList>> getCapabilitiesProxy();

    @NotNull
    Map<IO, Map<RecipeCapability<?>, List<IFilteredHandler>>> getCapabilitiesFlat();

    @NotNull
    default List<IFilteredHandler> getCapabilitiesFlat(IO io, RecipeCapability<?> cap) {
        return getCapabilitiesFlat()
                .getOrDefault(io, Collections.emptyMap())
                .getOrDefault(cap, Collections.emptyList());
    }

    default void addHandler(IFilteredHandler handler) {
        if (handler.getCapability() instanceof ContentRecipeCapability<?>) return;
        if (handler.isAvailable() && handler.getHandlerIO() != IO.NONE) {
            getCapabilitiesFlat().computeIfAbsent(handler.getHandlerIO(), i -> new Reference2ReferenceArrayMap<>(2)).computeIfAbsent(handler.getCapability(), c -> new ArrayList<>()).add(handler);
        }
    }

    default void addHandlerList(RecipeHandlerList handler) {
        if (handler == RecipeHandlerList.NO_DATA) return;
        IO io = handler.getHandlerIO();
        getCapabilitiesProxy().computeIfAbsent(io, i -> new ArrayList<>()).add(handler);
        var inner = getCapabilitiesFlat().computeIfAbsent(io, i -> new RecipeCapabilityMap<>());
        handler.handlerMap.forEach(entry -> {
            var entryList = entry.getValue();
            inner.computeIfAbsent(entry.getKey(), c -> new ArrayList<>(entryList.size())).addAll(entryList);
        });
    }

    default boolean matchRecipe(GTRecipe recipe) {
        return matchRecipeInput(recipe) && matchRecipeOutput(recipe);
    }

    default boolean matchRecipeInput(GTRecipe recipe) {
        return RecipeHelper.handleRecipe(this, recipe, IO.IN, recipe.inputs, Collections.emptyMap(), true);
    }

    default boolean matchRecipeOutput(GTRecipe recipe) {
        return RecipeHelper.handleRecipe(this, recipe, IO.OUT, recipe.outputs, Collections.emptyMap(), true);
    }

    default boolean handleRecipeInput(GTRecipe recipe) {
        return RecipeHelper.handleRecipe(this, recipe, IO.IN, recipe.inputs, Collections.emptyMap(), false);
    }

    default boolean handleRecipeOutput(GTRecipe recipe) {
        return RecipeHelper.handleRecipe(this, recipe, IO.OUT, recipe.outputs, Collections.emptyMap(), false);
    }

    default boolean matchRecipeTick(GTRecipe recipe) {
        return recipe.ticks.handleRecipe(this, recipe, true);
    }

    default boolean handleRecipeTick(GTRecipe recipe) {
        return recipe.ticks.handleRecipe(this, recipe, false);
    }
}
