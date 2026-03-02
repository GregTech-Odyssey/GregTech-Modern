package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.ITickSubscription;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IElectricItem;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.compat.FeCompat;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IFilteredHandler;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IExplosionMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandlerModifiable;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;
import java.util.function.Supplier;

public class NotifiableEnergyContainer extends NotifiableMachineTrait implements IEnergyContainer, IFilteredHandler {

    @Getter
    protected IO handlerIO;
    @Getter
    @Persisted
    @DescSynced
    public long energyStored;
    @Getter
    public long energyCapacity;
    @Getter
    protected long inputVoltage;
    @Getter
    protected long inputAmperage;
    @Getter
    protected long outputVoltage;
    @Getter
    protected long outputAmperage;
    @Setter
    protected Supplier<Direction[]> sideSupplier = () -> new Direction[] { machine.getFrontFacing() };
    @Setter
    protected Predicate<Direction> sideInputCondition;
    @Setter
    protected Predicate<Direction> sideOutputCondition;
    @Nullable
    protected TickableSubscription outputSubs;
    @Nullable
    protected TickableSubscription updateSubs;
    protected boolean checkOutput;
    protected boolean notify;

    public NotifiableEnergyContainer(MetaMachine machine, long maxCapacity, long maxInputVoltage, long maxInputAmperage, long maxOutputVoltage, long maxOutputAmperage) {
        super(machine);
        this.energyCapacity = maxCapacity;
        this.inputVoltage = maxInputVoltage;
        this.inputAmperage = maxInputAmperage;
        this.outputVoltage = maxOutputVoltage;
        this.outputAmperage = maxOutputAmperage;
        var isIn = (inputVoltage != 0 && inputAmperage != 0);
        var isOut = (outputVoltage != 0 && outputAmperage != 0);
        this.handlerIO = (isIn && isOut) ? IO.BOTH : isIn ? IO.IN : isOut ? IO.OUT : IO.NONE;
    }

    public static NotifiableEnergyContainer emitterContainer(MetaMachine machine, long maxCapacity, long maxOutputVoltage, long maxOutputAmperage) {
        return new NotifiableEnergyContainer(machine, maxCapacity, 0L, 0L, maxOutputVoltage, maxOutputAmperage);
    }

    public static NotifiableEnergyContainer receiverContainer(MetaMachine machine, long maxCapacity, long maxInputVoltage, long maxInputAmperage) {
        return new NotifiableEnergyContainer(machine, maxCapacity, maxInputVoltage, maxInputAmperage, 0L, 0L);
    }

    public void resetBasicInfo(long maxCapacity, long maxInputVoltage, long maxInputAmperage, long maxOutputVoltage, long maxOutputAmperage) {
        this.energyCapacity = maxCapacity;
        this.inputVoltage = maxInputVoltage;
        this.inputAmperage = maxInputAmperage;
        this.outputVoltage = maxOutputVoltage;
        this.outputAmperage = maxOutputAmperage;
        var isIN = (inputVoltage != 0 && inputAmperage != 0);
        var isOUT = (outputVoltage != 0 && outputAmperage != 0);
        this.handlerIO = (isIN && isOUT) ? IO.BOTH : isIN ? IO.IN : isOUT ? IO.OUT : IO.NONE;
        checkOutputSubscription();
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        if (machine.isRemote()) return;
        checkOutputSubscription();
        updateSubs = getMachine().subscribeServerTick(updateSubs, this::updateTick, 20);
    }

    @Override
    public void onMachineUnLoad() {
        super.onMachineUnLoad();
        if (updateSubs != null) {
            updateSubs.unsubscribe();
            updateSubs = null;
        }
    }

    public void checkOutputSubscription() {
        checkOutput = false;
        if (machine.getLevel() instanceof ServerLevel && outputVoltage > 0 && outputAmperage > 0 && getEnergyStored() >= 0) {
            outputSubs = machine.subscribeServerTick(outputSubs, this::serverTick);
        } else if (outputSubs != null) {
            outputSubs.unsubscribe();
            outputSubs = null;
        }
    }

    public void setEnergyStored(long energyStored) {
        if (this.energyStored == energyStored) return;
        this.energyStored = energyStored;
        checkOutput = true;
        notify = true;
    }

    public void updateTick() {
        if (checkOutput) {
            checkOutputSubscription();
        }
        if (notify) {
            notify = false;
            notifyListeners();
        }
    }

    public void serverTick() {
        long stored = getEnergyStored();
        if (stored > 0 && outputSubs != null) {
            long voltage = outputVoltage;
            long canOutput = Math.min(stored, outputAmperage * voltage);
            long energyUsed = 0;
            for (Direction side : sideSupplier.get()) {
                if (!outputsEnergy(side)) continue;
                var oppositeSide = side.getOpposite();
                var energyContainer = GTCapabilityHelper.getEnergyContainer(machine.getNeighbor(side), oppositeSide);
                if (energyContainer != null && energyContainer.inputsEnergy(oppositeSide)) {
                    energyUsed += energyContainer.acceptEnergyFromNetwork(this, oppositeSide, voltage, canOutput - energyUsed);
                    if (energyUsed == canOutput) break;
                }
            }
            if (energyUsed > 0) {
                setEnergyStored(stored - energyUsed);
                outputSubs.cycle = 0;
            } else if (outputSubs.cycle < 10) {
                outputSubs.cycle++;
            }
        } else {
            outputSubs = ITickSubscription.unsubscribe(outputSubs);
        }
    }

    public boolean dischargeOrRechargeEnergyContainers(IItemHandlerModifiable itemHandler, int slotIndex, boolean simulate) {
        var stackInSlot = itemHandler.getStackInSlot(slotIndex).copy();
        if (stackInSlot.isEmpty()) {
            // no stack to charge/discharge
            return false;
        }
        var electricItem = GTCapabilityHelper.getElectricItem(stackInSlot);
        if (electricItem != null) {
            if (handleElectricItem(electricItem, simulate)) {
                if (!simulate) {
                    itemHandler.setStackInSlot(slotIndex, stackInSlot);
                }
                return true;
            }
        } else if (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE) {
            IEnergyStorage energyStorage = GTCapabilityHelper.getForgeEnergyItem(stackInSlot);
            if (energyStorage != null && handleForgeEnergyItem(energyStorage, simulate)) {
                if (!simulate) {
                    itemHandler.setStackInSlot(slotIndex, stackInSlot);
                }
                return true;
            }
        }
        return false;
    }

    private boolean handleElectricItem(IElectricItem electricItem, boolean simulate) {
        var machineTier = GTUtil.getTierByVoltage(Math.max(inputVoltage, outputVoltage));
        var chargeTier = Math.min(machineTier, electricItem.getTier());
        var chargePercent = getEnergyStored() / (getEnergyCapacity() * 1.0);
        // Check if the item is a battery (or similar), and if we can receive some amount of energy
        if (electricItem.canProvideChargeExternally() && getEnergyCanBeInserted() > 0) {
            // Drain from the battery if we are below half energy capacity, and if the tier matches
            if (chargePercent <= 0.5 && chargeTier == machineTier) {
                long dischargedBy = electricItem.discharge(getEnergyCanBeInserted(), machineTier, false, true, simulate);
                if (!simulate) {
                    addEnergy(dischargedBy);
                }
                return dischargedBy > 0L;
            }
        }
        // Else, check if we have above 65% power
        if (chargePercent > 0.65) {
            long chargedBy = electricItem.charge(getEnergyStored(), chargeTier, false, simulate);
            if (!simulate) {
                removeEnergy(chargedBy);
            }
            return chargedBy > 0;
        }
        return false;
    }

    private boolean handleForgeEnergyItem(IEnergyStorage energyStorage, boolean simulate) {
        int machineTier = GTUtil.getTierByVoltage(Math.max(inputVoltage, outputVoltage));
        double chargePercent = getEnergyStored() / (getEnergyCapacity() * 1.0);
        if (chargePercent > 0.65) {
            // 2/3rds full
            long chargedBy = FeCompat.insertEu(energyStorage, GTValues.V[machineTier], simulate);
            if (!simulate) {
                removeEnergy(chargedBy);
            }
            return chargedBy > 0;
        }
        return false;
    }

    @Override
    public long acceptEnergyFromNetwork(Object o, Direction side, long voltage, long energyAdded) {
        if (side == null || inputsEnergy(side)) {
            if (voltage > inputVoltage && machine instanceof IExplosionMachine explosionMachine) {
                explosionMachine.doExplosion(GTUtil.getTierByVoltage(voltage));
                return 0;
            }
            long stored = getEnergyStored();
            energyAdded = Math.min(getEnergyCapacity() - stored, Math.min(energyAdded, voltage * getInputAmperage()));
            if (energyAdded > 0) {
                setEnergyStored(stored + energyAdded);
                return energyAdded;
            }
        }
        return 0;
    }

    @Override
    public boolean inputsEnergy(Direction side) {
        return inputVoltage > 0 && (sideInputCondition == null || sideInputCondition.test(side));
    }

    @Override
    public boolean outputsEnergy(Direction side) {
        return outputVoltage > 0 && (sideOutputCondition == null || sideOutputCondition.test(side));
    }

    @Override
    public long changeEnergy(long energyToAdd) {
        if (energyToAdd == 0) return 0;
        final long stored = getEnergyStored();
        if (energyToAdd > 0) {
            energyToAdd = Math.min(energyToAdd, getEnergyCapacity() - stored);
            setEnergyStored(stored + energyToAdd);
            return energyToAdd;
        } else {
            energyToAdd = Math.max(energyToAdd, -stored);
            setEnergyStored(stored + energyToAdd);
            return energyToAdd;
        }
    }

    @Override
    public RecipeCapability<?> getCapability() {
        return EURecipeCapability.CAP;
    }
}
