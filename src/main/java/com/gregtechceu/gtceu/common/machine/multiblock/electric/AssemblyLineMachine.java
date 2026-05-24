package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.ActionResult;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
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
        if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineItems) {
            if (!consumeOrderedItemInputs(recipe, true)) {
                setIdleReason(ActionResult.FAIL_ORDERED_ITEM::reason);
                return false;
            }
        } else {
            if (!unit.handleRecipeItem(IO.IN, recipe, recipe.itemInputs, true)) {
                return false;
            }
        }
        if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineFluids) {
            if (!consumeOrderedFluidInputs(recipe, true)) {
                setIdleReason(ActionResult.FAIL_ORDERED_FLUID::reason);
                return false;
            }
            return true;
        } else {
            return unit.handleRecipeFluid(IO.IN, recipe, recipe.fluidInputs, true);
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
        if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineItems) {
            if (!consumeOrderedItemInputs(recipe, false)) {
                return false;
            }
        } else {
            if (!unit.handleRecipeItem(IO.IN, recipe, recipe.itemInputs, false)) {
                return false;
            }
        }
        if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineFluids) {
            return consumeOrderedFluidInputs(recipe, false);
        } else {
            return unit.handleRecipeFluid(IO.IN, recipe, recipe.fluidInputs, false);
        }
    }

    private boolean consumeOrderedItemInputs(GTRecipe recipe, boolean simulate) {
        var itemInputs = recipe.itemInputs;
        if (itemInputs.isEmpty()) return true;

        var machineInputs = itemStackTransfers;
        if (machineInputs.size() < itemInputs.size()) return false;

        for (int i = 0; i < itemInputs.size(); i++) {
            var inputSlot = machineInputs.get(i);
            var recipeInput = itemInputs.get(i);
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

    private boolean consumeOrderedFluidInputs(GTRecipe recipe, boolean simulate) {
        var fluidInputs = recipe.fluidInputs;
        if (fluidInputs.isEmpty()) return true;

        var machineInputs = fluidStackTransfers;
        if (machineInputs.size() < fluidInputs.size()) return false;

        for (int i = 0; i < fluidInputs.size(); i++) {
            var inputTank = machineInputs.get(i);
            var recipeInput = fluidInputs.get(i);
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
