package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.VoidFluidHandler;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class DistillationTowerMachine extends WorkableElectricMultiblockMachine {

    @Getter
    private List<IFluidHandler> fluidOutputs;
    @Nullable
    private IFluidHandler firstValid = null;
    private final int yOffset;

    public DistillationTowerMachine(MetaMachineBlockEntity holder) {
        this(holder, 1);
    }

    /**
     * Construct DT Machine
     * 
     * @param holder  BlockEntity holder
     * @param yOffset The Y difference between the controller and the first fluid output
     */
    public DistillationTowerMachine(MetaMachineBlockEntity holder, int yOffset) {
        super(holder);
        this.yOffset = yOffset;
    }

    @Override
    public RecipeLogic createRecipeLogic(Object... args) {
        return new DistillationTowerLogic(this);
    }

    @Override
    public DistillationTowerLogic getRecipeLogic() {
        return (DistillationTowerLogic) super.getRecipeLogic();
    }

    @Override
    public Comparator<IMultiPart> getPartSorter() {
        return Comparator.comparingInt(p -> p.self().getPos().getY());
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        final int startY = getPos().getY() + yOffset;
        List<IWorkableMultiPart> parts = Arrays.stream(getParts()).filter(IWorkableMultiPart.class::isInstance).map(IWorkableMultiPart.class::cast).filter(part -> PartAbility.EXPORT_FLUIDS.isApplicable(part.self().getBlockState().getBlock())).filter(part -> part.self().getPos().getY() >= startY).toList();
        if (!parts.isEmpty()) {
            // Loop from controller y + offset -> the highest output hatch
            int maxY = parts.getLast().self().getPos().getY();
            fluidOutputs = new ArrayList<>(maxY - startY);
            int outputIndex = 0;
            for (int y = startY; y <= maxY; ++y) {
                if (parts.size() <= outputIndex) {
                    fluidOutputs.add(VoidFluidHandler.INSTANCE);
                    continue;
                }
                var part = parts.get(outputIndex);
                if (part.self().getPos().getY() == y) {
                    var handler = part.getRecipeHandlers().getFirst().getCapability(FluidRecipeCapability.CAP).stream().filter(IFluidHandler.class::isInstance).findFirst().map(IFluidHandler.class::cast).orElse(VoidFluidHandler.INSTANCE);
                    addOutput(handler);
                    outputIndex++;
                } else if (part.self().getPos().getY() > y) {
                    fluidOutputs.add(VoidFluidHandler.INSTANCE);
                } else {
                    GTCEu.LOGGER.error("The Distillation Tower at {} has a fluid export hatch with an unexpected Y position", getPos());
                    onStructureInvalid();
                    return;
                }
            }
        } else onStructureInvalid();
    }

    private void addOutput(IFluidHandler handler) {
        fluidOutputs.add(handler);
        if (firstValid == null && handler != VoidFluidHandler.INSTANCE) firstValid = handler;
    }

    @Override
    public void onStructureInvalid() {
        fluidOutputs = null;
        firstValid = null;
        super.onStructureInvalid();
    }

    public static class DistillationTowerLogic extends RecipeLogic {

        @Nullable
        @Persisted
        GTRecipe workingRecipe = null;

        public DistillationTowerLogic(IRecipeLogicMachine machine) {
            super(machine);
        }

        @Override
        public DistillationTowerMachine getMachine() {
            return (DistillationTowerMachine) super.getMachine();
        }

        // Copy of lastRecipe with fluid outputs trimmed, for output displays like Jade or GUI text
        @Override
        @Nullable
        public GTRecipe getLastRecipe() {
            return workingRecipe;
        }

        @Override
        protected boolean matchRecipe(GTRecipe recipe) {
            var match = matchDTRecipe(recipe);
            if (!match) return false;
            return RecipeHelper.matchTickRecipe(this.machine, recipe);
        }

        @Override
        public void findAndHandleRecipe() {
            workingRecipe = null;
            super.findAndHandleRecipe();
        }

        private boolean matchDTRecipe(GTRecipe recipe) {
            var result = RecipeHelper.handleRecipe(machine, recipe, IO.IN, recipe.inputs, Collections.emptyMap(), true);
            if (!result) return false;
            var items = recipe.getOutputContents(ItemRecipeCapability.CAP);
            if (!items.isEmpty()) {
                Map<RecipeCapability<?>, List<Content>> out = Map.of(ItemRecipeCapability.CAP, items);
                result = RecipeHelper.handleRecipe(machine, recipe, IO.OUT, out, Collections.emptyMap(), true);
                if (!result) return false;
            }
            return applyFluidOutputs(recipe, FluidAction.SIMULATE);
        }

        private void updateWorkingRecipe(GTRecipe recipe) {
            if (recipe.recipeType == GTRecipeTypes.DISTILLERY_RECIPES) {
                this.workingRecipe = recipe;
                return;
            }
            this.workingRecipe = recipe.copy();
            var contents = recipe.getOutputContents(FluidRecipeCapability.CAP);
            var outputs = getMachine().getFluidOutputs();
            List<Content> trimmed = new ArrayList<>(12);
            for (int i = 0; i < Math.min(contents.size(), outputs.size()); ++i) {
                if (!(outputs.get(i) instanceof VoidFluidHandler)) trimmed.add(contents.get(i));
            }
            this.workingRecipe.outputs.put(FluidRecipeCapability.CAP, trimmed);
        }

        @Override
        protected boolean handleRecipeIO(GTRecipe recipe, IO io) {
            if (io != IO.OUT) {
                var handleIO = super.handleRecipeIO(recipe, io);
                if (handleIO) {
                    updateWorkingRecipe(recipe);
                } else {
                    this.workingRecipe = null;
                }
                return handleIO;
            }
            var items = recipe.getOutputContents(ItemRecipeCapability.CAP);
            if (!items.isEmpty()) {
                Map<RecipeCapability<?>, List<Content>> out = Map.of(ItemRecipeCapability.CAP, items);
                RecipeHelper.handleRecipe(this.machine, recipe, io, out, chanceCaches, false);
            }
            if (applyFluidOutputs(recipe, FluidAction.EXECUTE)) {
                workingRecipe = null;
                return true;
            }
            return false;
        }

        private boolean applyFluidOutputs(GTRecipe recipe, FluidAction action) {
            var fluids = recipe.getOutputContents(FluidRecipeCapability.CAP).stream().map(FluidRecipeCapability.CAP::of).toList();
            // Distillery recipes should output to the first non-void handler
            if (recipe.recipeType == GTRecipeTypes.DISTILLERY_RECIPES) {
                var fluid = fluids.getFirst().getLatestStacks()[0];
                var handler = getMachine().getFirstValid();
                if (handler == null) return false;
                int filled = (handler instanceof NotifiableFluidTank nft) ? nft.fillInternal(fluid, action) : handler.fill(fluid, action);
                return filled == fluid.getAmount();
            }
            boolean valid = true;
            var outputs = getMachine().getFluidOutputs();
            for (int i = 0; i < Math.min(fluids.size(), outputs.size()); ++i) {
                var handler = outputs.get(i);
                var fluid = fluids.get(i).getLatestStacks()[0];
                int filled = (handler instanceof NotifiableFluidTank nft) ? nft.fillInternal(fluid, action) : handler.fill(fluid, action);
                if (filled != fluid.getAmount()) valid = false;
                if (action.simulate() && !valid) break;
            }
            return valid;
        }
    }

    @Nullable
    public IFluidHandler getFirstValid() {
        return this.firstValid;
    }
}
