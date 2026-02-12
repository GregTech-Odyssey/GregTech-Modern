package com.gregtechceu.gtceu.common.machine.trait.customlogic;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

public enum CannerLogic implements GTRecipeType.ICustomRecipeLogic {

    INSTANCE;

    @Override
    public @Nullable GTRecipeDefinition createCustomRecipe(IRecipeCapabilityHolder holder) {
        var handlerLists = holder.getInputList();
        if (handlerLists.isEmpty()) return null;
        List<RecipeHandlerList> distinct = new ArrayList<>();
        List<IRecipeHandler<?>> notDistinctItems = new ArrayList<>();
        List<IRecipeHandler<?>> notDistinctFluids = new ArrayList<>();

        for (var handlerList : handlerLists) {
            if (handlerList.isDistinct()) {
                distinct.add(handlerList);
            } else {
                notDistinctItems.addAll(handlerList.getCapability(ItemRecipeCapability.CAP));
                notDistinctFluids.addAll(handlerList.getCapability(FluidRecipeCapability.CAP));
            }
        }

        if (distinct.isEmpty() && notDistinctItems.isEmpty() && notDistinctFluids.isEmpty()) return null;

        List<ItemStack> itemStacks = new ArrayList<>();
        List<FluidStack> fluidStacks = new ArrayList<>();

        List<Pair<ItemStack, IFluidHandlerItem>> validItems = new ArrayList<>();
        List<FluidStack> validFluids = new ArrayList<>();

        for (var rhl : distinct) {
            itemStacks.clear();
            fluidStacks.clear();
            if (!collect(rhl, itemStacks, fluidStacks)) continue;

            for (var itemStack : itemStacks) {
                var single = itemStack.copyWithCount(1);
                var copy = itemStack.copyWithCount(1);
                var fluidHandler = FluidUtil.getFluidHandler(copy).orElse(null);
                if (fluidHandler == null) continue;
                // Try to drain first
                var fluid = fluidHandler.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
                if (!fluid.isEmpty()) {
                    return GTRecipeTypes.CANNER_RECIPES
                            .recipeBuilder("drain_fluid")
                            .inputItems(single)
                            .outputItems(fluidHandler.getContainer())
                            .outputFluids(fluid)
                            .duration(Math.max(16, fluid.getAmount() / 64))
                            .EUt(4)
                            .build();
                }

                for (var fluidStack : fluidStacks) {
                    var fluidCopy = fluidStack.copy();
                    var filled = fluidHandler.fill(fluidCopy, FluidAction.EXECUTE);
                    if (filled == 0) continue;
                    fluidCopy.setAmount(filled);
                    return GTRecipeTypes.CANNER_RECIPES
                            .recipeBuilder("fill_fluid")
                            .inputItems(single)
                            .inputFluids(fluidCopy)
                            .outputItems(fluidHandler.getContainer())
                            .duration(Math.max(16, filled / 64))
                            .EUt(4)
                            .build();
                }
                validItems.add(Pair.of(copy, fluidHandler));
            }
            validFluids.addAll(fluidStacks);
        }

        itemStacks.clear();
        fluidStacks.clear();
        collect(notDistinctItems, notDistinctFluids, itemStacks, fluidStacks);
        if (itemStacks.isEmpty() && validItems.isEmpty()) return null; // no items to fill/drain
        fluidStacks.addAll(validFluids);

        for (var itemStack : itemStacks) {
            var single = itemStack.copyWithCount(1);
            var copy = itemStack.copyWithCount(1);
            var fluidHandler = FluidUtil.getFluidHandler(copy).orElse(null);
            if (fluidHandler == null) continue;
            // Try to drain first
            var fluid = fluidHandler.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
            if (!fluid.isEmpty()) {
                return GTRecipeTypes.CANNER_RECIPES
                        .recipeBuilder("drain_fluid")
                        .inputItems(single)
                        .outputItems(fluidHandler.getContainer())
                        .outputFluids(fluid)
                        .duration(Math.max(16, fluid.getAmount() / 64))
                        .EUt(4)
                        .build();
            }

            for (var fluidStack : fluidStacks) {
                var fluidCopy = fluidStack.copy();
                var filled = fluidHandler.fill(fluidCopy, FluidAction.EXECUTE);
                if (filled == 0) continue;
                fluidCopy.setAmount(filled);
                return GTRecipeTypes.CANNER_RECIPES
                        .recipeBuilder("fill_fluid")
                        .inputItems(single)
                        .inputFluids(fluidCopy)
                        .outputItems(fluidHandler.getContainer())
                        .duration(Math.max(16, filled / 64))
                        .EUt(4)
                        .build();
            }
        }

        if (fluidStacks.isEmpty()) return null;

        // Try filling containers from distinct handlers with fluids from indistinct handlers
        // We already tried draining them on L72
        for (var pair : validItems) {
            var stack = pair.getFirst();
            var single = stack.copyWithCount(1);
            var fluidHandler = pair.getSecond();

            for (var fluidStack : fluidStacks) {
                var fluidCopy = fluidStack.copy();
                var filled = fluidHandler.fill(fluidCopy, FluidAction.EXECUTE);
                if (filled == 0) continue;
                fluidCopy.setAmount(filled);
                return GTRecipeTypes.CANNER_RECIPES
                        .recipeBuilder("fill_fluid")
                        .inputItems(single)
                        .inputFluids(fluidCopy)
                        .outputItems(fluidHandler.getContainer())
                        .duration(Math.max(16, filled / 64))
                        .EUt(4)
                        .build();
            }
        }

        return null;
    }

    private static boolean collect(RecipeHandlerList rhl, List<ItemStack> itemStacks, List<FluidStack> fluidStacks) {
        rhl.fastForEach((stack, amount) -> itemStacks.add(stack), (stack, amount) -> fluidStacks.add(stack));
        return !(itemStacks.isEmpty() || fluidStacks.isEmpty());
    }

    private static void collect(List<IRecipeHandler<?>> itemHandlers, List<IRecipeHandler<?>> fluidHandlers,
                                List<ItemStack> itemStacks, List<FluidStack> fluidStacks) {
        for (var handler : itemHandlers) {
            if (!handler.shouldSearchContent()) continue;
            handler.fastForEachItems((stack, amount) -> itemStacks.add(stack));
        }

        for (var handler : fluidHandlers) {
            if (!handler.shouldSearchContent()) continue;
            handler.fastForEachFluids((stack, amount) -> fluidStacks.add(stack));
        }
    }
}
