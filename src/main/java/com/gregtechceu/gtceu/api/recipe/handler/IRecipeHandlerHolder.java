package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IElectricMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineFeature;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.recipe.*;
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

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;

public interface IRecipeHandlerHolder extends IMachineFeature {

    default boolean hasCapabilityProxies() {
        return !getCapabilitiesProxy().isEmpty();
    }

    default List<RecipeHandlerUnit> getInputUnits() {
        return getCapabilitiesProxy().getOrDefault(IO.IN, Collections.emptyList());
    }

    default List<RecipeHandlerUnit> getOutputUnits() {
        return getCapabilitiesProxy().getOrDefault(IO.OUT, Collections.emptyList());
    }

    default List<RecipeHandlerUnit> getOutputUnits(GTRecipe recipe) {
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
        if (unit == RecipeHandlerUnit.NO_DATA || unit.handlerIO == IO.NONE || unit.allHandlers.length == 0) return;
        getCapabilitiesProxy().computeIfAbsent(unit.handlerIO, i -> new ArrayList<>()).add(unit);
        var list = getCapabilitiesFlat().computeIfAbsent(unit.handlerIO, i -> new ArrayList<>());
        for (var handler : unit.allHandlers) {
            if (list.contains(handler)) continue;
            list.add(handler);
        }
    }

    default boolean usePrioritySearch() {
        return false;
    }

    default boolean alwaysSearchRecipe() {
        return false;
    }

    @SuppressWarnings("UnusedReturnValue")
    default boolean findRecipe(GTRecipeType type, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> canHandle) {
        if (usePrioritySearch()) return prioritySearch(type, this, canHandle);
        var customRecipeLogic = type.getCustomRecipeLogicRunners();
        var hasCustomRecipeLogic = !customRecipeLogic.isEmpty();
        for (var unit : this.getInputUnits()) {
            if (unit.findRecipe(type, canHandle)) return true;
            if (hasCustomRecipeLogic) {
                for (var logic : customRecipeLogic) {
                    var r = logic.createCustomRecipe(this, unit);
                    if (r != null && canHandle.test(unit, r)) return true;
                }
            }
        }
        return false;
    }

    static boolean prioritySearch(GTRecipeType type, IRecipeHandlerHolder holder, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> canHandle) {
        var recipes = new ArrayList<Pair<RecipeHandlerUnit, GTRecipeDefinition>>();
        for (var list : holder.getInputUnits()) {
            list.findRecipe(type, (u, r) -> {
                recipes.add(Pair.of(u, r));
                return false;
            });
            recipes.sort(Comparator.comparingInt(p -> -p.getSecond().priority));
            for (var p : recipes) {
                if (canHandle.test(p.getFirst(), p.getSecond())) return true;
            }
        }
        return false;
    }

    default void setIdleReason(Supplier<Component> reason) {}

    default boolean checkConditions(RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        if (recipe.conditions.length == 0) return true;
        Map<Class<?>, List<RecipeCondition>> or = new Reference2ObjectArrayMap<>();
        for (RecipeCondition condition : recipe.conditions) {
            if (condition.isOr()) {
                or.computeIfAbsent(condition.getClass(), type -> new ArrayList<>()).add(condition);
            } else if (!condition.check(this, unit, recipe)) {
                setIdleReason(() -> ActionResult.failCondition(condition.getTooltips()).reason());
                return false;
            }
        }

        for (List<RecipeCondition> conditions : or.values()) {
            boolean passed = conditions.isEmpty();
            MutableComponent component = Component.translatable("gtceu.recipe_logic.condition_fails").append(": ");
            for (RecipeCondition condition : conditions) {
                passed = condition.check(this, unit, recipe);
                if (passed) break;
                else component.append(condition.getTooltips());
            }

            if (!passed) {
                setIdleReason(() -> component);
                return false;
            }
        }
        return true;
    }

    default boolean checkTier(GTRecipeDefinition recipe) {
        int tier = recipe.tier;
        if (tier > 0 && this instanceof ITieredMachine tieredMachine) {
            if (tier > tieredMachine.getRecipeTier()) {
                setIdleReason(ActionResult.FAIL_INSUFFICIENT_TIER::reason);
                return false;
            }
        }
        return true;
    }

    default boolean matchRecipe(RecipeHandlerUnit unit, GTRecipe recipe) {
        return matchRecipeInput(unit, recipe) && matchRecipeOutput(recipe);
    }

    default boolean matchRecipeInput(RecipeHandlerUnit unit, GTRecipe recipe) {
        var items = RecipeHelper.copyContents(recipe.itemInputs, 1);
        var fluids = RecipeHelper.copyContents(recipe.fluidInputs, 1);
        if (unit.handleRecipeItem(IO.IN, recipe, items, true) && unit.handleRecipeFluid(IO.IN, recipe, fluids, true)) {
            for (var e : recipe.definition.contentExpanders) {
                if (!e.handle(this, null, recipe, true)) return false;
            }
            return true;
        }
        return false;
    }

    default boolean matchRecipeOutput(GTRecipe recipe) {
        var items = RecipeHelper.copyContents(recipe.itemOutputs, 1);
        var fluids = RecipeHelper.copyContents(recipe.fluidOutputs, 1);
        if (items.isEmpty() && fluids.isEmpty()) return true;
        for (var handler : getOutputUnits(recipe)) {
            if (handler.handleRecipeItem(IO.OUT, recipe, items, true) && handler.handleRecipeFluid(IO.OUT, recipe, fluids, true)) {
                return true;
            }
        }
        setIdleReason(ActionResult.FAIL_INSUFFICIENT_OUT::reason);
        return false;
    }

    default boolean handleRecipeInput(RecipeHandlerUnit unit, GTRecipe recipe) {
        var items = RecipeHelper.copyAndRoll(recipe, recipe.itemInputs);
        var fluids = RecipeHelper.copyAndRoll(recipe, recipe.fluidInputs);
        if (unit.handleRecipeItem(IO.IN, recipe, items, false) && unit.handleRecipeFluid(IO.IN, recipe, fluids, false)) {
            for (var e : recipe.definition.contentExpanders) {
                if (!e.handle(this, null, recipe, false)) return false;
            }
            return true;
        }
        return false;
    }

    default boolean handleRecipeOutput(GTRecipe recipe) {
        var items = RecipeHelper.copyAndRoll(recipe, recipe.itemOutputs);
        var fluids = RecipeHelper.copyAndRoll(recipe, recipe.fluidOutputs);
        for (var handler : getOutputUnits(recipe)) {
            if (handler.handleRecipeItem(IO.OUT, recipe, items, false) && handler.handleRecipeFluid(IO.OUT, recipe, fluids, false)) {
                return true;
            }
        }
        return false;
    }

    default boolean matchTickRecipe(GTRecipe recipe) {
        var eu = recipe.eut;
        if (eu != 0) {
            if (!(this instanceof IElectricMachine electricMachine && electricMachine.useEnergy(eu, true))) {
                setIdleReason(() -> ActionResult.failInsufficientIn(EURecipeCapability.CAP.getName()).reason());
                return false;
            }
        }
        for (var e : recipe.definition.tickContentExpanders) {
            if (!e.handle(this, null, recipe, true)) return false;
        }
        return true;
    }

    default boolean handleTickRecipe(GTRecipe recipe) {
        var eu = recipe.eut;
        if (eu != 0) {
            if (!(this instanceof IElectricMachine electricMachine && electricMachine.useEnergy(eu, false))) {
                setIdleReason(() -> ActionResult.failInsufficientIn(EURecipeCapability.CAP.getName()).reason());
                return false;
            }
        }
        for (var e : recipe.definition.tickContentExpanders) {
            if (!e.handle(this, null, recipe, false)) return false;
        }
        return true;
    }

    default int getCircuit(boolean sum) {
        int circuit = 0;
        for (var handler : getInputUnits()) {
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
        for (var handler : getInputUnits()) {
            handler.getFluidAmount(consumable, fluids, amounts);
        }
        return amounts;
    }

    default long[] getItemAmount(boolean consumable, Item... items) {
        long[] amounts = new long[items.length];
        for (var handler : getInputUnits()) {
            handler.getItemAmount(consumable, items, amounts);
        }
        return amounts;
    }

    default boolean forEachItems(boolean consumable, ObjLongPredicate<ItemStack> function) {
        for (var handler : getInputUnits()) {
            if (handler.forEachItems(consumable, function)) return true;
        }
        return false;
    }

    default boolean forEachFluids(boolean consumable, ObjLongPredicate<FluidStack> function) {
        for (var handler : getInputUnits()) {
            if (handler.forEachFluids(consumable, function)) return true;
        }
        return false;
    }

    default void fastForEachItems(boolean consumable, ObjLongConsumer<ItemStack> function) {
        getInputUnits().forEach(h -> h.fastForEachItems(consumable, function));
    }

    default void fastForEachFluids(boolean consumable, ObjLongConsumer<FluidStack> function) {
        getInputUnits().forEach(h -> h.fastForEachFluids(consumable, function));
    }

    default boolean inputItem(ItemLike item, long amount) {
        for (var handler : getInputUnits()) {
            if (handler.inputItem(item, amount)) return true;
        }
        return false;
    }

    default boolean inputItem(ItemStack... items) {
        for (var handler : getInputUnits()) {
            if (handler.inputItem(items)) return true;
        }
        return false;
    }

    default boolean outputItem(ItemLike item, long amount) {
        var contentList = new ArrayList<Content<ItemIngredient>>(1);
        contentList.add(new Content<>(ItemIngredient.of(item, amount)));
        for (var handler : getOutputUnits()) {
            if (handler.handleItem(IO.OUT, contentList, false)) return true;
        }
        return false;
    }

    default boolean outputItem(ItemStack... items) {
        var contentList = RecipeHandlerUnit.toItemIngredient(items);
        for (var handler : getOutputUnits()) {
            if (handler.handleItem(IO.OUT, contentList, false)) return true;
        }
        return false;
    }

    default boolean matchItem(ItemLike item, long amount) {
        for (var handler : getInputUnits()) {
            if (handler.matchItem(item, amount)) return true;
        }
        return false;
    }

    default boolean matchItem(ItemStack... items) {
        for (var handler : getInputUnits()) {
            if (handler.matchItem(items)) return true;
        }
        return false;
    }

    default boolean matchCircuit(int configuration) {
        for (var handler : getInputUnits()) {
            if (handler.matchCircuit(configuration)) return true;
        }
        return false;
    }

    default boolean inputFluid(Fluid fluid, long amount) {
        for (var handler : getInputUnits()) {
            if (handler.inputFluid(fluid, amount)) return true;
        }
        return false;
    }

    default boolean inputFluid(FluidStack... fluids) {
        for (var handler : getInputUnits()) {
            if (handler.inputFluid(fluids)) return true;
        }
        return false;
    }

    default boolean outputFluid(Fluid fluid, long amount) {
        var contentList = new ArrayList<Content<FluidIngredient>>(1);
        contentList.add(new Content<>(FluidIngredient.of(fluid, amount)));
        for (var handler : getOutputUnits()) {
            if (handler.handleFluid(IO.OUT, contentList, false)) return true;
        }
        return false;
    }

    default boolean outputFluid(FluidStack... fluids) {
        var contentList = RecipeHandlerUnit.toFluidIngredient(fluids);
        for (var handler : getOutputUnits()) {
            if (handler.handleFluid(IO.OUT, contentList, false)) return true;
        }
        return false;
    }

    default boolean matchFluid(Fluid fluid, long amount) {
        for (var handler : getInputUnits()) {
            if (handler.matchFluid(fluid, amount)) return true;
        }
        return false;
    }

    default boolean matchFluid(FluidStack... fluids) {
        for (var handler : getInputUnits()) {
            if (handler.matchFluid(fluids)) return true;
        }
        return false;
    }
}
