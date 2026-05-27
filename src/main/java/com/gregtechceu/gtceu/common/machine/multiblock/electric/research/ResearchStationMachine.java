package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IObjectHolder;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.item.ItemStack;

import lombok.Getter;

import javax.annotation.ParametersAreNonnullByDefault;

@Getter
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class ResearchStationMachine extends WorkableElectricMultiblockMachine {

    private IObjectHolder objectHolder;

    public ResearchStationMachine(MetaMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public long requestCWU(long cwut, boolean simulate) {
        long cwu = super.requestCWU(cwut, simulate);
        if (!simulate && cwu >= cwut) {
            var progress = getRecipeLogic().getProgress();
            getRecipeLogic().setProgress(progress + (int) Math.min(getRecipeLogic().getMaxProgress() - progress, cwu));
        }
        return cwu;
    }

    @Override
    public boolean hasBatchConfig() {
        return false;
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        for (IMultiPart part : getParts()) {
            if (part instanceof IObjectHolder iObjectHolder) {
                this.objectHolder = iObjectHolder;
                addHandlerList(RecipeHandlerUnit.of(IO.IN, iObjectHolder.getAsHandler()));
                break;
            }
        }
        // should never happen, but would rather do this than have an obscure NPE
        if (objectHolder == null) {
            onStructureInvalid();
        }
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

    @Override
    public boolean matchRecipeOutput(GTRecipe recipe) {
        return true;
    }

    @Override
    public boolean handleRecipeInput(RecipeHandlerUnit unit, GTRecipe recipe) {
        if (super.handleRecipeInput(unit, recipe)) {
            if (objectHolder != null) objectHolder.setLocked(true);
            return true;
        }
        return false;
    }

    @Override
    public void afterWorking() {
        super.afterWorking();
        if (objectHolder != null) objectHolder.setLocked(false);
    }

    @Override
    public boolean handleRecipeOutput(GTRecipe recipe) {
        IObjectHolder holder = objectHolder;
        if (holder == null) return true;
        holder.setLocked(false);
        holder.setHeldItem(ItemStack.EMPTY);
        ItemStack outputItem = ItemStack.EMPTY;
        var contents = recipe.itemOutputs;
        if (!contents.isEmpty()) {
            outputItem = contents.getFirst().inner.getInnerItemStack();
        }
        if (!outputItem.isEmpty()) {
            holder.setDataItem(outputItem.copyWithCount(1));
        }
        return true;
    }
}
