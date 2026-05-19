package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IElectricMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineFeature;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.utils.function.ObjLongPredicate;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.ObjLongConsumer;

public interface IRecipeHandlerHolder extends IMachineFeature {

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
        List<Content<ItemIngredient>> items = GTRecipe.copyContents(recipe.itemOutputs, 1);
        List<Content<FluidIngredient>> fluids = GTRecipe.copyContents(recipe.fluidOutputs, 1);
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

    default int getCircuit(boolean sum) {
        int circuit = 0;
        for (var handler : getInputList()) {
            var c = handler.getCircuit(sum);
            if (c > 0) {
                circuit += c;
                if (!sum) break;
            }
        }
        return circuit;
    }

    default long[] getFluidAmount(boolean consumable, Fluid... fluids) {
        long[] amounts = new long[fluids.length];
        for (var handler : getInputList()) {
            handler.getFluidAmount(consumable, fluids, amounts);
        }
        return amounts;
    }

    default long[] getItemAmount(boolean consumable, Item... items) {
        long[] amounts = new long[items.length];
        for (var handler : getInputList()) {
            handler.getItemAmount(consumable, items, amounts);
        }
        return amounts;
    }

    default boolean forEachItems(boolean consumable, ObjLongPredicate<ItemStack> function) {
        for (var handler : getInputList()) {
            if (handler.forEachItems(consumable, function)) return true;
        }
        return false;
    }

    default boolean forEachFluids(boolean consumable, ObjLongPredicate<FluidStack> function) {
        for (var handler : getInputList()) {
            if (handler.forEachFluids(consumable, function)) return true;
        }
        return false;
    }

    default void fastForEachItems(boolean consumable, ObjLongConsumer<ItemStack> function) {
        getInputList().forEach(h -> h.fastForEachItems(consumable, function));
    }

    default void fastForEachFluids(boolean consumable, ObjLongConsumer<FluidStack> function) {
        getInputList().forEach(h -> h.fastForEachFluids(consumable, function));
    }

    default boolean inputItem(ItemLike item, long amount) {
        for (var handler : getInputList()) {
            if (handler.inputItem(item, amount)) return true;
        }
        return false;
    }

    default boolean inputItem(ItemStack... items) {
        for (var handler : getInputList()) {
            if (handler.inputItem(items)) return true;
        }
        return false;
    }

    default boolean outputItem(ItemLike item, long amount) {
        var contentList = new ArrayList<Content<ItemIngredient>>(1);
        contentList.add(new Content<>(ItemIngredient.of(item, amount)));
        for (var handler : getOutputList()) {
            if (handler.handleItem(IO.OUT, contentList, false)) return true;
        }
        return false;
    }

    default boolean outputItem(ItemStack... items) {
        var contentList = RecipeHandlerUnit.toItemIngredient(items);
        for (var handler : getOutputList()) {
            if (handler.handleItem(IO.OUT, contentList, false)) return true;
        }
        return false;
    }

    default boolean matchItem(ItemLike item, long amount) {
        for (var handler : getInputList()) {
            if (handler.matchItem(item, amount)) return true;
        }
        return false;
    }

    default boolean matchItem(ItemStack... items) {
        for (var handler : getInputList()) {
            if (handler.matchItem(items)) return true;
        }
        return false;
    }

    default boolean matchCircuit(int configuration) {
        for (var handler : getInputList()) {
            if (handler.matchCircuit(configuration)) return true;
        }
        return false;
    }

    default boolean inputFluid(Fluid fluid, long amount) {
        for (var handler : getInputList()) {
            if (handler.inputFluid(fluid, amount)) return true;
        }
        return false;
    }

    default boolean inputFluid(FluidStack... fluids) {
        for (var handler : getInputList()) {
            if (handler.inputFluid(fluids)) return true;
        }
        return false;
    }

    default boolean outputFluid(Fluid fluid, long amount) {
        var contentList = new ArrayList<Content<FluidIngredient>>(1);
        contentList.add(new Content<>(FluidIngredient.of(fluid, amount)));
        for (var handler : getOutputList()) {
            if (handler.handleFluid(IO.OUT, contentList, false)) return true;
        }
        return false;
    }

    default boolean outputFluid(FluidStack... fluids) {
        var contentList = RecipeHandlerUnit.toFluidIngredient(fluids);
        for (var handler : getOutputList()) {
            if (handler.handleFluid(IO.OUT, contentList, false)) return true;
        }
        return false;
    }

    default boolean matchFluid(Fluid fluid, long amount) {
        for (var handler : getInputList()) {
            if (handler.matchFluid(fluid, amount)) return true;
        }
        return false;
    }

    default boolean matchFluid(FluidStack... fluids) {
        for (var handler : getInputList()) {
            if (handler.matchFluid(fluids)) return true;
        }
        return false;
    }
}
