package com.gregtechceu.gtceu.api.capability.recipe;

import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface IRecipeCapabilityHolder {

    default boolean hasCapabilityProxies() {
        return !getCapabilitiesProxy().isEmpty();
    }

    default boolean isChange() {
        return true;
    }

    default void setChange(boolean cache) {}

    @Nullable
    default RecipeHandlerList getCurrentHandlerList() {
        return null;
    }

    default void setCurrentHandlerList(RecipeHandlerList list, GTRecipe recipe) {}

    @NotNull
    Map<IO, List<RecipeHandlerList>> getCapabilitiesProxy();

    @NotNull
    Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> getCapabilitiesFlat();

    @NotNull
    default List<RecipeHandlerList> getCapabilitiesForIO(IO io) {
        return getCapabilitiesProxy().getOrDefault(io, Collections.emptyList());
    }

    @NotNull
    default List<IRecipeHandler<?>> getCapabilitiesFlat(IO io, RecipeCapability<?> cap) {
        return getCapabilitiesFlat()
                .getOrDefault(io, Collections.emptyMap())
                .getOrDefault(cap, Collections.emptyList());
    }

    default void addHandlerList(RecipeHandlerList handler) {
        if (handler == RecipeHandlerList.NO_DATA) return;
        IO io = handler.getHandlerIO();
        getCapabilitiesProxy().computeIfAbsent(io, i -> new ObjectArrayList<>()).add(handler);
        var entrySet = handler.handlerMap.entrySet();
        var inner = getCapabilitiesFlat().computeIfAbsent(io, i -> new Reference2ObjectOpenHashMap<>(entrySet.size()));
        for (var entry : entrySet) {
            var entryList = entry.getValue();
            inner.computeIfAbsent(entry.getKey(), c -> new ObjectArrayList<>(entryList.size())).addAll(entryList);
        }
    }
}
