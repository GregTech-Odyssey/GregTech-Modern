package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class AssemblyLineMachine extends WorkableElectricMultiblockMachine {

    private List<CustomItemStackHandler> itemStackTransfers = new ArrayList<>();
    private List<CustomFluidTank> fluidStackTransfers = new ArrayList<>();

    public AssemblyLineMachine(MetaMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public boolean beforeWorking(@NotNull GTRecipe recipe) {
        if (!super.beforeWorking(recipe)) return false;
        var config = ConfigHolder.INSTANCE.machines;
        if (!config.orderedAssemblyLineItems && !config.orderedAssemblyLineFluids) return true;
        if (!checkItemInputs(recipe)) return false;
        if (!config.orderedAssemblyLineFluids) return true;
        return checkFluidInputs(recipe);
    }

    @Override
    public RecipeLogic createRecipeLogic(Object... args) {
        return new AssemblyLineLogic(this);
    }

    @Override
    public Comparator<IMultiPart> getPartSorter() {
        return Comparator.comparing(p -> p.self().getPos(), RelativeDirection.RIGHT.getSorter(getFrontFacing(), getUpwardsFacing(), isFlipped()));
    }

    private boolean checkItemInputs(@NotNull GTRecipe recipe) {
        var itemInputs = recipe.inputs.getOrDefault(ItemRecipeCapability.CAP, Collections.emptyList());
        if (itemInputs.isEmpty()) return true;
        int inputsSize = itemInputs.size();
        if (itemStackTransfers.size() < inputsSize) return false;
        var itemInventory = itemStackTransfers.stream().map(container -> container.getStackInSlot(0)).filter(i -> !i.isEmpty()).limit(inputsSize).toList();
        if (itemInventory.size() < inputsSize) return false;
        for (int i = 0; i < inputsSize; i++) {
            if (!ItemRecipeCapability.CAP.of(itemInputs.get(i)).test(itemInventory.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean checkFluidInputs(@NotNull GTRecipe recipe) {
        var fluidInputs = recipe.inputs.getOrDefault(FluidRecipeCapability.CAP, Collections.emptyList());
        if (fluidInputs.isEmpty()) return true;
        int inputsSize = fluidInputs.size();
        if (fluidStackTransfers.size() < inputsSize) return false;
        var fluidInventory = fluidStackTransfers.stream().map(FluidTank::getFluid).filter(f -> !f.isEmpty()).limit(inputsSize).toList();
        if (fluidInventory.size() < inputsSize) return false;
        for (int i = 0; i < inputsSize; i++) {
            if (!FluidRecipeCapability.CAP.of(fluidInputs.get(i)).test(fluidInventory.get(i))) {
                return false;
            }
        }
        return true;
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

    public static class AssemblyLineLogic extends RecipeLogic {

        public AssemblyLineLogic(IRecipeLogicMachine machine) {
            super(machine);
        }

        @NotNull
        @Override
        public AssemblyLineMachine getMachine() {
            return (AssemblyLineMachine) super.getMachine();
        }

        @Override
        protected boolean handleRecipeIO(GTRecipe recipe, IO io) {
            if (io == IO.IN) {
                if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineItems) {
                    if (!consumeOrderedItemInputs(recipe)) {
                        return false;
                    }
                } else {
                    var items = recipe.getInputContents(ItemRecipeCapability.CAP);
                    if (!RecipeHelper.handleRecipe(this.machine, recipe, io, Map.of(ItemRecipeCapability.CAP, items), chanceCaches, false)) {
                        return false;
                    }
                }
                if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineFluids) {
                    return consumeOrderedFluidInputs(recipe);
                } else {
                    var fluids = recipe.getInputContents(FluidRecipeCapability.CAP);
                    return RecipeHelper.handleRecipe(this.machine, recipe, io, Map.of(FluidRecipeCapability.CAP, fluids), chanceCaches, false);
                }
            } else {
                return super.handleRecipeIO(recipe, io);
            }
        }

        private boolean consumeOrderedItemInputs(GTRecipe recipe) {
            var itemInputs = recipe.inputs.getOrDefault(ItemRecipeCapability.CAP, Collections.emptyList());
            if (itemInputs.isEmpty()) return true;

            var machineInputs = getMachine().itemStackTransfers;
            if (machineInputs.size() < itemInputs.size()) return false;

            for (int i = 0; i < itemInputs.size(); i++) {
                var inputSlot = machineInputs.get(i);
                var recipeInput = ItemRecipeCapability.CAP.of(itemInputs.get(i));

                if (inputSlot.getStackInSlot(0).isEmpty() ||
                        !recipeInput.test(inputSlot.getStackInSlot(0))) {
                    return false;
                }

                inputSlot.extractItem(0, recipeInput.getAmount(), false);
            }
            return true;
        }

        private boolean consumeOrderedFluidInputs(GTRecipe recipe) {
            var fluidInputs = recipe.inputs.getOrDefault(FluidRecipeCapability.CAP, Collections.emptyList());
            if (fluidInputs.isEmpty()) return true;

            var machineInputs = getMachine().fluidStackTransfers;
            if (machineInputs.size() < fluidInputs.size()) return false;

            for (int i = 0; i < fluidInputs.size(); i++) {
                var inputTank = machineInputs.get(i);
                var recipeInput = FluidRecipeCapability.CAP.of(fluidInputs.get(i));

                if (inputTank.getFluid().isEmpty() ||
                        !recipeInput.test(inputTank.getFluid())) {
                    return false;
                }

                inputTank.drain(recipeInput.getAmount(), IFluidHandler.FluidAction.EXECUTE);
            }
            return true;
        }
    }
}
