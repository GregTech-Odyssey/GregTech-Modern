package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.api.capability.IObjectHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class ResearchStationMachine extends WorkableElectricMultiblockMachine {

    private IObjectHolder objectHolder;

    public ResearchStationMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public RecipeLogic createRecipeLogic(Object... args) {
        return new ResearchStationRecipeLogic(this);
    }

    @Override
    public @NotNull ResearchStationRecipeLogic getRecipeLogic() {
        return (ResearchStationRecipeLogic) super.getRecipeLogic();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        for (IMultiPart part : getParts()) {
            if (part instanceof IObjectHolder iObjectHolder) {
                if (iObjectHolder.getFrontFacing() != getFrontFacing().getOpposite()) {
                    onStructureInvalid();
                    return;
                }
                this.objectHolder = iObjectHolder;
                addHandlerList(RecipeHandlerList.of(IO.IN, iObjectHolder.getAsHandler()));
            }
        }
        // should never happen, but would rather do this than have an obscure NPE
        if (objectHolder == null) {
            onStructureInvalid();
        }
    }

    @Override
    public boolean checkPattern() {
        boolean isFormed = super.checkPattern();
        if (isFormed && objectHolder != null && objectHolder.getFrontFacing() != getFrontFacing().getOpposite()) {
            onStructureInvalid();
        }
        return isFormed;
    }

    @Override
    public void onStructureInvalid() {
        // recheck the ability to make sure it wasn't the one broken
        for (IMultiPart part : getParts()) {
            if (part instanceof IObjectHolder holder) {
                if (holder == objectHolder) {
                    objectHolder.setLocked(false);
                }
            }
        }
        objectHolder = null;
        super.onStructureInvalid();
    }

    @Override
    public boolean regressWhenWaiting() {
        return false;
    }

    public static class ResearchStationRecipeLogic extends RecipeLogic {

        public ResearchStationRecipeLogic(ResearchStationMachine metaTileEntity) {
            super(metaTileEntity);
        }

        @NotNull
        @Override
        public ResearchStationMachine getMachine() {
            return (ResearchStationMachine) super.getMachine();
        }

        // skip "can fit" checks, it can always fit
        @Override
        protected boolean matchRecipe(GTRecipe recipe) {
            var match = matchRecipeNoOutput(recipe);
            if (!match) return false;
            return matchTickRecipeNoOutput(recipe);
        }

        protected boolean matchRecipeNoOutput(GTRecipe recipe) {
            if (!machine.hasCapabilityProxies()) return false;
            return RecipeHelper.handleRecipe(machine, recipe, IO.IN, recipe.inputs, Collections.emptyMap(), false, true);
        }

        protected boolean matchTickRecipeNoOutput(GTRecipe recipe) {
            if (recipe.hasTick()) {
                if (!machine.hasCapabilityProxies()) return false;
                return RecipeHelper.handleRecipe(machine, recipe, IO.IN, recipe.tickInputs, Collections.emptyMap(), false, true);
            }
            return true;
        }

        // Handle RecipeIO manually
        @Override
        protected boolean handleRecipeIO(GTRecipe recipe, IO io) {
            if (io == IO.IN) {
                // lock the object holder on recipe start
                IObjectHolder holder = getMachine().getObjectHolder();
                holder.setLocked(true);
                return true;
            }
            // "replace" the items in the slots rather than outputting elsewhere
            // unlock the object holder
            IObjectHolder holder = getMachine().getObjectHolder();
            if (lastRecipe == null) {
                holder.setLocked(false);
                return true;
            }
            holder.setHeldItem(ItemStack.EMPTY);
            ItemStack outputItem = ItemStack.EMPTY;
            var contents = lastRecipe.getOutputContents(ItemRecipeCapability.CAP);
            if (!contents.isEmpty()) {
                outputItem = ItemRecipeCapability.CAP.of(contents.get(0).content).getItems()[0];
            }
            if (!outputItem.isEmpty()) {
                holder.setDataItem(outputItem);
            }
            holder.setLocked(false);
            return true;
        }

        @Override
        protected boolean handleTickRecipeIO(GTRecipe recipe, IO io) {
            if (io != IO.OUT) {
                return super.handleTickRecipeIO(recipe, io);
            }
            return true;
        }
    }

    public IObjectHolder getObjectHolder() {
        return this.objectHolder;
    }
}
