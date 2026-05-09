package com.gregtechceu.gtceu.api.recipe.handler;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface IRecipeHandlerHolder {

    default boolean hasCapabilityProxies() {
        return !getCapabilitiesProxy().isEmpty();
    }

    default List<RecipeHandlerUnit> getInputList() {
        return getCapabilitiesProxy().getOrDefault(IO.IN, Collections.emptyList());
    }

    default List<RecipeHandlerUnit> getOutputList() {
        return getCapabilitiesProxy().getOrDefault(IO.OUT, Collections.emptyList());
    }

    @NotNull
    Map<IO, List<RecipeHandlerUnit>> getCapabilitiesProxy();

    @NotNull
    Map<IO, List<IRecipeHandler>> getCapabilitiesFlat();

    @NotNull
    default List<IRecipeHandler> getCapabilitiesFlat(IO io) {
        return getCapabilitiesFlat().getOrDefault(io, Collections.emptyList());
    }

    default void addHandlerList(RecipeHandlerUnit handler) {
        if (handler == RecipeHandlerUnit.NO_DATA || handler.getHandlerIO() == IO.NONE) return;
        getCapabilitiesProxy().computeIfAbsent(handler.getHandlerIO(), i -> new ArrayList<>()).add(handler);
        getCapabilitiesFlat().computeIfAbsent(handler.getHandlerIO(), i -> new ArrayList<>()).addAll(handler.allHandlers);
    }
}
