package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IElectricItem;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.compat.FeCompat;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IExplosionMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class NotifiableEnergyContainer extends NotifiableRecipeHandlerTrait<Long> implements IEnergyContainer {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(NotifiableEnergyContainer.class, NotifiableRecipeHandlerTrait.MANAGED_FIELD_HOLDER);
    protected IO handlerIO;
    @Persisted
    @DescSynced
    protected long energyStored;
    private long energyCapacity;
    private long inputVoltage;
    private long inputAmperage;
    private long outputVoltage;
    private long outputAmperage;
    private Predicate<Direction> sideInputCondition;
    private Predicate<Direction> sideOutputCondition;
    @Nullable
    protected TickableSubscription outputSubs;
    @Nullable
    protected TickableSubscription updateSubs;
    protected long lastEnergyInputPerSec = 0;
    protected long lastEnergyOutputPerSec = 0;
    protected long energyInputPerSec = 0;
    protected long energyOutputPerSec = 0;
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
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        if (machine.isRemote()) return;
        checkOutputSubscription();
        updateSubs = getMachine().subscribeServerTick(updateSubs, this::updateTick);
    }

    @Override
    public void onMachineUnLoad() {
        super.onMachineUnLoad();
        if (updateSubs != null) {
            updateSubs.unsubscribe();
            updateSubs = null;
        }
    }

    protected void notifyOutputSubscription() {
        checkOutput = true;
    }

    public void checkOutputSubscription() {
        checkOutput = false;
        if (machine.getLevel() instanceof ServerLevel) {
            if (getOutputVoltage() > 0 && getOutputAmperage() > 0) {
                if (getEnergyStored() >= 0) {
                    outputSubs = machine.subscribeServerTick(outputSubs, this::serverTick);
                } else if (outputSubs != null) {
                    outputSubs.unsubscribe();
                    outputSubs = null;
                }
            }
        }
    }

    @Override
    public long getInputPerSec() {
        return lastEnergyInputPerSec;
    }

    @Override
    public long getOutputPerSec() {
        return lastEnergyOutputPerSec;
    }

    public void setEnergyStored(long energyStored) {
        if (this.energyStored == energyStored) return;
        if (energyStored > this.energyStored) {
            energyInputPerSec += energyStored - this.energyStored;
        } else {
            energyOutputPerSec += this.energyStored - energyStored;
        }
        this.energyStored = energyStored;
        notifyOutputSubscription();
        notify = true;
    }

    public void updateTick() {
        if (checkOutput) {
            checkOutputSubscription();
        }
        if (getMachine().getOffsetTimer() % 20 == 0) {
            if (notify) {
                notify = false;
                notifyListeners();
            }
            lastEnergyOutputPerSec = energyOutputPerSec;
            lastEnergyInputPerSec = energyInputPerSec;
            energyOutputPerSec = 0;
            energyInputPerSec = 0;
        }
    }

    public void serverTick() {
        long stored = getEnergyStored();
        if (stored >= 0) {
            long voltage = getOutputVoltage();
            long canOutput = Math.min(stored, getOutputAmperage() * voltage);
            long energyUsed = 0;
            for (Direction side : GTUtil.DIRECTIONS) {
                if (!outputsEnergy(side)) continue;
                var oppositeSide = side.getOpposite();
                var energyContainer = GTCapabilityHelper.getEnergyContainer(machine.getNeighbor(side), oppositeSide);
                if (energyContainer != null && energyContainer.inputsEnergy(oppositeSide)) {
                    energyUsed += energyContainer.acceptEnergyFromNetwork(oppositeSide, voltage, canOutput - energyUsed);
                    if (energyUsed == canOutput) break;
                }
            }
            if (energyUsed > 0) {
                setEnergyStored(stored - energyUsed);
            }
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
        var machineTier = GTUtil.getTierByVoltage(Math.max(getInputVoltage(), getOutputVoltage()));
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
        int machineTier = GTUtil.getTierByVoltage(Math.max(getInputVoltage(), getOutputVoltage()));
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
    public long acceptEnergyFromNetwork(Direction side, long voltage, long energyAdded) {
        if (side == null || inputsEnergy(side)) {
            long inputVoltage = getInputVoltage();
            if (voltage > inputVoltage && machine instanceof IExplosionMachine explosionMachine) {
                explosionMachine.doExplosion(GTUtil.getTierByVoltage(voltage));
                return 0;
            }
            long stored = getEnergyStored();
            energyAdded = Math.min(getEnergyCapacity() - stored, Math.min(energyAdded, inputVoltage * getInputAmperage()));
            if (energyAdded > 0) {
                setEnergyStored(stored + energyAdded);
                return energyAdded;
            }
        }
        return 0;
    }

    @Override
    public boolean inputsEnergy(Direction side) {
        return !outputsEnergy(side) && getInputVoltage() > 0 && (sideInputCondition == null || sideInputCondition.test(side));
    }

    @Override
    public boolean outputsEnergy(Direction side) {
        return getOutputVoltage() > 0 && (sideOutputCondition == null || sideOutputCondition.test(side));
    }

    @Override
    public long changeEnergy(long energyToAdd) {
        long oldEnergyStored = getEnergyStored();
        long newEnergyStored = (energyCapacity - oldEnergyStored < energyToAdd) ? energyCapacity : (oldEnergyStored + energyToAdd);
        if (newEnergyStored < 0) newEnergyStored = 0;
        setEnergyStored(newEnergyStored);
        return newEnergyStored - oldEnergyStored;
    }

    @Override
    public List<Long> handleRecipeInner(IO io, GTRecipe recipe, List<Long> left, boolean simulate) {
        IEnergyContainer capability = this;
        long sum = left.stream().reduce(0L, Long::sum);
        if (io == IO.IN) {
            var canOutput = capability.getEnergyStored();
            if (!simulate) {
                capability.addEnergy(-Math.min(canOutput, sum));
            }
            sum = sum - canOutput;
        } else if (io == IO.OUT) {
            long canInput = capability.getEnergyCapacity() - capability.getEnergyStored();
            if (!simulate) {
                capability.addEnergy(Math.min(canInput, sum));
            }
            sum = sum - canInput;
        }
        return sum <= 0 ? null : Collections.singletonList(sum);
    }

    @Override
    public double getTotalContentAmount() {
        return energyStored;
    }

    @Override
    public RecipeCapability<Long> getCapability() {
        return EURecipeCapability.CAP;
    }

    public IO getHandlerIO() {
        return this.handlerIO;
    }

    public long getEnergyStored() {
        return this.energyStored;
    }

    public long getEnergyCapacity() {
        return this.energyCapacity;
    }

    public long getInputVoltage() {
        return this.inputVoltage;
    }

    public long getInputAmperage() {
        return this.inputAmperage;
    }

    public long getOutputVoltage() {
        return this.outputVoltage;
    }

    public long getOutputAmperage() {
        return this.outputAmperage;
    }

    public void setSideInputCondition(final Predicate<Direction> sideInputCondition) {
        this.sideInputCondition = sideInputCondition;
    }

    public void setSideOutputCondition(final Predicate<Direction> sideOutputCondition) {
        this.sideOutputCondition = sideOutputCondition;
    }
}
