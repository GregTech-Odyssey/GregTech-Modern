package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.ActionResult;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraftforge.fluids.capability.IFluidHandler;

import java.util.*;

public class AssemblyLineMachine extends WorkableElectricMultiblockMachine {

    private List<CustomItemStackHandler> itemStackTransfers = new ArrayList<>();
    private List<CustomFluidTank> fluidStackTransfers = new ArrayList<>();

    public AssemblyLineMachine(MetaMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public boolean matchRecipe(RecipeHandlerUnit unit, GTRecipe recipe) {
        if (!super.matchRecipeOutput(recipe)) return false;
        var items = RecipeHelper.copyContents(recipe.itemInputs, 1);
        var fluids = RecipeHelper.copyContents(recipe.fluidInputs, 1);
        if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineItems) {
            if (!consumeOrderedItemInputs(items, true)) {
                setIdleReason(ActionResult.FAIL_ORDERED_ITEM);
                return false;
            }
        } else {
            if (!unit.handleRecipeItem(IO.IN, recipe, items, true)) {
                return false;
            }
        }
        if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineFluids) {
            if (!consumeOrderedFluidInputs(fluids, true)) {
                setIdleReason(ActionResult.FAIL_ORDERED_FLUID);
                return false;
            }
            return true;
        } else {
            return unit.handleRecipeFluid(IO.IN, recipe, fluids, true);
        }
    }

    @Override
    public Comparator<IMultiPart> getPartSorter() {
        return Comparator.comparing(p -> p.self().getPos(), RelativeDirection.RIGHT.getSorter(getFrontFacing(), getUpwardsFacing(), isFlipped()));
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        itemStackTransfers = new ArrayList<>();
        fluidStackTransfers = new ArrayList<>();
        for (Object part : getParts()) {
            if (part instanceof ItemBusPartMachine itemBusPart) {
                itemStackTransfers.add(itemBusPart.getInventory().storage);
            } else if (part instanceof FluidHatchPartMachine fluidHatch) {
                fluidStackTransfers.add(fluidHatch.tank.getStorages()[0]);
            }
        }
    }

    @Override
    public boolean handleRecipeInput(RecipeHandlerUnit unit, GTRecipe recipe) {
        var items = RecipeHelper.copyAndRoll(recipe, recipe.itemInputs);
        var fluids = RecipeHelper.copyAndRoll(recipe, recipe.fluidInputs);
        if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineItems) {
            if (!consumeOrderedItemInputs(items, false)) {
                return false;
            }
        } else {
            if (!unit.handleRecipeItem(IO.IN, recipe, items, false)) {
                return false;
            }
        }
        if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineFluids) {
            return consumeOrderedFluidInputs(fluids, false);
        } else {
            return unit.handleRecipeFluid(IO.IN, recipe, fluids, false);
        }
    }

    private boolean consumeOrderedItemInputs(List<Content<ItemIngredient>> items, boolean simulate) {
        if (items.isEmpty()) return true;
        var machineInputs = itemStackTransfers;
        if (machineInputs.size() < items.size()) return false;

        for (int i = 0; i < items.size(); i++) {
            var inputSlot = machineInputs.get(i);
            var recipeInput = items.get(i);
            var stack = inputSlot.getStackInSlot(0);
            if (stack.getCount() < recipeInput.amount ||
                    !recipeInput.inner.test(stack)) {
                return false;
            }
            if (simulate) continue;
            inputSlot.extractItem(0, recipeInput.getIntAmount(), false);
        }
        return true;
    }

    private boolean consumeOrderedFluidInputs(List<Content<FluidIngredient>> fluids, boolean simulate) {
        if (fluids.isEmpty()) return true;
        var machineInputs = fluidStackTransfers;
        if (machineInputs.size() < fluids.size()) return false;

        for (int i = 0; i < fluids.size(); i++) {
            var inputTank = machineInputs.get(i);
            var recipeInput = fluids.get(i);
            var stack = inputTank.getFluid();
            if (stack.getAmount() < recipeInput.amount ||
                    !recipeInput.inner.test(stack)) {
                return false;
            }
            if (simulate) continue;
            inputTank.drain(recipeInput.getIntAmount(), IFluidHandler.FluidAction.EXECUTE);
        }
        return true;
    }
}
