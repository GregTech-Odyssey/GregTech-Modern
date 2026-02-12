package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IDataAccessHatch;
import com.gregtechceu.gtceu.api.capability.IOpticalDataAccessHatch;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.common.blockentity.OpticalPipeBlockEntity;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.DataBankMachine;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class OpticalDataHatchMachine extends MultiblockPartMachine implements IOpticalDataAccessHatch {

    @Getter
    private final boolean isTransmitter;
    private boolean call;

    public OpticalDataHatchMachine(MetaMachineBlockEntity holder, boolean isTransmitter) {
        super(holder);
        this.isTransmitter = isTransmitter;
    }

    @Override
    public void updateRecipeLogic() {
        if (call) return;
        call = true;
        if (isTransmitter) {
            IDataAccessHatch cap = getAccessHatch();
            if (cap != null) {
                cap.updateRecipeLogic();
            }
        } else {
            for (var controller : getControllers()) {
                if (controller instanceof IRecipeLogicMachine recipeLogicMachine) {
                    recipeLogicMachine.getRecipeLogic().updateTickSubscription();
                }
            }
        }
        call = false;
    }

    @Override
    public boolean isRecipeAvailable(GTRecipeDefinition recipe) {
        if (!isFormed()) {
            return false;
        }
        if (call) return false;
        call = true;
        var result = false;
        if (isTransmitter) {
            if (getController() instanceof DataBankMachine dataBankMachine && dataBankMachine.getRecipeLogic().isWorking()) {
                result = isRecipeAvailable(dataBankMachine.dataAccesses, recipe) || isRecipeAvailable(dataBankMachine.receptions, recipe);
            }
        } else {
            IDataAccessHatch cap = getAccessHatch();
            result = cap != null && cap.isRecipeAvailable(recipe);
        }
        call = false;
        return result;
    }

    private static boolean isRecipeAvailable(Iterable<? extends IDataAccessHatch> hatches, GTRecipeDefinition recipe) {
        for (IDataAccessHatch hatch : hatches) {
            if (hatch.isRecipeAvailable(recipe)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }

    @Override
    public boolean canShared() {
        return false;
    }

    protected @Nullable IDataAccessHatch getAccessHatch() {
        if (getNeighbor(getFrontFacing()) instanceof OpticalPipeBlockEntity blockEntity) {
            return blockEntity.getCapability(GTCapability.CAPABILITY_DATA_ACCESS, getFrontFacing().getOpposite()).orElse(null);
        }
        return null;
    }
}
