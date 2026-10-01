package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.issue.GTIssues;
import com.gregtechceu.gtceu.api.machine.issue.IssueStage;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblockpro.ParamKey;
import com.gregtechceu.gtceu.api.machine.multiblockpro.PortKey;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
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

    public static final PortKey SECTION_IN = PortKey.IN;
    public static final PortKey SECTION_OUT = PortKey.OUT;
    public static final ParamKey SECTIONS = ParamKey.of("gtceu.multiblock.assembly_line.sections", "gtceu.multiblock.assembly_line.sections.desc");

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
            int failed = consumeOrderedItemInputs(items, true);
            if (failed >= 0) {
                reportIssue(GTIssues.ORDERED_INPUT, null, IO.IN, ItemRecipeInfo.INSTANCE, failed, 0, 0, recipe.definition);
                return false;
            }
        } else {
            if (!unit.handleRecipeItem(IO.IN, recipe, items, true)) {
                reportIssue(GTIssues.INPUT_SHORT, null, IO.IN, ItemRecipeInfo.INSTANCE, -1, -1, -1, recipe.definition);
                return false;
            }
        }
        if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineFluids) {
            int failed = consumeOrderedFluidInputs(fluids, true);
            if (failed >= 0) {
                reportIssue(GTIssues.ORDERED_INPUT, null, IO.IN, FluidRecipeInfo.INSTANCE, failed, 0, 0, recipe.definition);
                return false;
            }
            return true;
        } else {
            if (!unit.handleRecipeFluid(IO.IN, recipe, fluids, true)) {
                reportIssue(GTIssues.INPUT_SHORT, null, IO.IN, FluidRecipeInfo.INSTANCE, -1, -1, -1, recipe.definition);
                return false;
            }
            return true;
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
            int failed = consumeOrderedItemInputs(items, false);
            if (failed >= 0) {
                reportIssue(GTIssues.ORDERED_INPUT, IssueStage.SETUP, IO.IN, ItemRecipeInfo.INSTANCE, failed, 0, 0, recipe.definition);
                return false;
            }
        } else {
            if (!unit.handleRecipeItem(IO.IN, recipe, items, false)) {
                reportIssue(GTIssues.INPUT_SHORT, IssueStage.SETUP, IO.IN, ItemRecipeInfo.INSTANCE, -1, -1, -1, recipe.definition);
                return false;
            }
        }
        if (ConfigHolder.INSTANCE.machines.orderedAssemblyLineFluids) {
            int failed = consumeOrderedFluidInputs(fluids, false);
            if (failed >= 0) {
                reportIssue(GTIssues.ORDERED_INPUT, IssueStage.SETUP, IO.IN, FluidRecipeInfo.INSTANCE, failed, 0, 0, recipe.definition);
                return false;
            }
            return true;
        } else {
            if (!unit.handleRecipeFluid(IO.IN, recipe, fluids, false)) {
                reportIssue(GTIssues.INPUT_SHORT, IssueStage.SETUP, IO.IN, FluidRecipeInfo.INSTANCE, -1, -1, -1, recipe.definition);
                return false;
            }
            return true;
        }
    }

    private int consumeOrderedItemInputs(List<Content<ItemIngredient>> items, boolean simulate) {
        if (items.isEmpty()) return -1;
        var machineInputs = itemStackTransfers;
        if (machineInputs.size() < items.size()) return machineInputs.size();

        for (int i = 0; i < items.size(); i++) {
            var inputSlot = machineInputs.get(i);
            var recipeInput = items.get(i);
            var stack = inputSlot.getStackInSlot(0);
            if (stack.getCount() < recipeInput.amount ||
                    !recipeInput.inner.test(stack)) {
                return i;
            }
            if (simulate) continue;
            inputSlot.extractItem(0, recipeInput.getIntAmount(), false);
        }
        return -1;
    }

    private int consumeOrderedFluidInputs(List<Content<FluidIngredient>> fluids, boolean simulate) {
        if (fluids.isEmpty()) return -1;
        var machineInputs = fluidStackTransfers;
        if (machineInputs.size() < fluids.size()) return machineInputs.size();

        for (int i = 0; i < fluids.size(); i++) {
            var inputTank = machineInputs.get(i);
            var recipeInput = fluids.get(i);
            var stack = inputTank.getFluid();
            if (stack.getAmount() < recipeInput.amount ||
                    !recipeInput.inner.test(stack)) {
                return i;
            }
            if (simulate) continue;
            inputTank.drain(recipeInput.getIntAmount(), IFluidHandler.FluidAction.EXECUTE);
        }
        return -1;
    }
}
