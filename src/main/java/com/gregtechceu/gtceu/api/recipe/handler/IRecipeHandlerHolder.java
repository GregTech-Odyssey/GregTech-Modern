package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.recipe.*;

import org.jetbrains.annotations.NotNull;

import java.util.*;

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

    @NotNull
    @Deprecated
    default List<IRecipeHandler> getCapabilitiesFlat(IO io, RecipeCapability<?> capability) {
        var all = getCapabilitiesFlat(io);
        if (all.isEmpty()) return Collections.emptyList();
        var list = new ArrayList<IRecipeHandler>();
        for (var handler : all) {
            if (ItemRecipeCapability.CAP == capability && handler.canHandleItem()) {
                list.add(handler);
            } else if (FluidRecipeCapability.CAP == capability && handler.canHandleFluid()) {
                list.add(handler);
            } else if (EURecipeCapability.CAP == capability && handler instanceof IEnergyContainer) {
                list.add(handler);
            } else if (CWURecipeCapability.CAP == capability && handler instanceof IOpticalComputationProvider) {
                list.add(handler);
            }
        }
        return list;
    }

    @NotNull
    default List<IRecipeHandler> getItemCapabilitiesFlat(IO io) {
        var all = getCapabilitiesFlat(io);
        if (all.isEmpty()) return Collections.emptyList();
        var list = new ArrayList<IRecipeHandler>();
        for (var handler : all) {
            if (handler.canHandleItem()) {
                list.add(handler);
            }
        }
        return list;
    }

    @NotNull
    default List<IRecipeHandler> getFluidCapabilitiesFlat(IO io) {
        var all = getCapabilitiesFlat(io);
        if (all.isEmpty()) return Collections.emptyList();
        var list = new ArrayList<IRecipeHandler>();
        for (var handler : all) {
            if (handler.canHandleFluid()) {
                list.add(handler);
            }
        }
        return list;
    }

    @NotNull
    default <T> List<T> getCapabilitiesFlat(IO io, Class<T> capabilitie) {
        var all = getCapabilitiesFlat(io);
        if (all.isEmpty()) return Collections.emptyList();
        var list = new ArrayList<T>();
        all.forEach(h -> {
            if (capabilitie.isInstance(h)) {
                list.add((T) h);
            }
        });
        return list;
    }

    default void addHandlerList(RecipeHandlerUnit unit) {
        if (unit == RecipeHandlerUnit.NO_DATA || unit.getHandlerIO() == IO.NONE || unit.allHandlers.length == 0) return;
        getCapabilitiesProxy().computeIfAbsent(unit.getHandlerIO(), i -> new ArrayList<>()).add(unit);
        var list = getCapabilitiesFlat().computeIfAbsent(unit.getHandlerIO(), i -> new ArrayList<>());
        for (var handler : unit.allHandlers) {
            if (list.contains(handler)) continue;
            list.add(handler);
        }
    }
}
