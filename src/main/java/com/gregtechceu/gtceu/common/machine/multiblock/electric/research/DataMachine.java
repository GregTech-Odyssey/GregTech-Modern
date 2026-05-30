package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.handler.ActionResult;
import com.gregtechceu.gtceu.api.recipe.info.EURecipeInfo;
import com.gregtechceu.gtceu.utils.TaskHandler;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.Nullable;

import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
abstract class DataMachine extends WorkableElectricMultiblockMachine {

    public static final int EUT_PER_HATCH = GTValues.VA[GTValues.EV];
    public static final int EUT_PER_HATCH_CHAINED = GTValues.VA[GTValues.LuV];
    protected int energyUsage = 0;
    @Nullable
    protected TickableSubscription tickSubs;

    public DataMachine(MetaMachineBlockEntity holder) {
        super(holder);
        this.energyContainer = EnergyContainerList.EMPTY;
    }

    @Override
    public boolean hasBatchConfig() {
        return false;
    }

    @Override
    protected void onStructureFormedAfter() {
        super.onStructureFormedAfter();
        updateTickSubscription();
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        this.energyUsage = calculateEnergyUsage();
    }

    protected int calculateEnergyUsage() {
        int receivers = 0;
        int transmitters = 0;
        int regulars = 0;
        for (var part : this.getParts()) {
            Block block = part.self().getBlockState().getBlock();
            if (PartAbility.OPTICAL_DATA_RECEPTION.isApplicable(block)) {
                ++receivers;
            }
            if (PartAbility.OPTICAL_DATA_TRANSMISSION.isApplicable(block)) {
                ++transmitters;
            }
            if (PartAbility.DATA_ACCESS.isApplicable(block)) {
                ++regulars;
            }
        }
        int dataHatches = receivers + transmitters + regulars;
        int eutPerHatch = receivers > 0 ? EUT_PER_HATCH_CHAINED : EUT_PER_HATCH;
        return eutPerHatch * dataHatches;
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        updateTickSubscription();
        this.energyUsage = 0;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.isFormed() && getLevel() instanceof ServerLevel serverLevel) {
            TaskHandler.enqueueTask(serverLevel, this::updateTickSubscription, 0);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
    }

    protected void updateTickSubscription() {
        if (isFormed) {
            tickSubs = subscribeServerTick(tickSubs, this::tick);
        } else if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
    }

    public void tick() {
        if (this.energyContainer.removeEnergy(energyUsage) >= energyUsage) {
            getRecipeLogic().setStatus(RecipeLogic.WORKING);
        } else {
            getRecipeLogic().setWaiting(ActionResult.failInsufficientIn(EURecipeInfo.INSTANCE.getName()).reason());
        }
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed()).setWorkingStatus(true, isActive() && isWorkingEnabled()).setWorkingStatusKeys("gtceu.multiblock.idling", "gtceu.multiblock.idling", "gtceu.multiblock.data_bank.providing").addEnergyUsageExactLine(energyUsage).addWorkingStatusLine();
    }

    @Override
    public int getProgress() {
        return 0;
    }

    @Override
    public int getMaxProgress() {
        return 0;
    }
}
