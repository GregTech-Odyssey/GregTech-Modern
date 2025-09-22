package com.gregtechceu.gtceu.api.misc;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.core.Direction;

import java.util.List;

public class EnergyContainerList implements IEnergyContainer {

    public static final EnergyContainerList EMPTY = new EnergyContainerList();
    private static final int dcpMode = ConfigHolder.INSTANCE.machines.dualChamberPressurizationMode;

    private final IEnergyContainer[] energyContainerList;
    private final long inputVoltage;
    private final long outputVoltage;
    private final long inputAmperage;
    private final long outputAmperage;
    private final long overclockVoltage;
    private final long maxVoltage;
    private final long capacity;

    public EnergyContainerList(List<IEnergyContainer> energyContainerList) {
        this(energyContainerList.toArray(new IEnergyContainer[0]));
    }

    public EnergyContainerList(IEnergyContainer... energyContainerList) {
        this.energyContainerList = energyContainerList;
        long input = 0;
        long output = 0;
        long maxRecipe = 0;
        long minRecipe = GTValues.V[GTValues.MAX];
        int count = 0;
        int maxCount = 0;
        long capacity = 0;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            capacity += iEnergyContainer.getEnergyCapacity();
            long voltage = iEnergyContainer.getInputVoltage();
            long amperage = iEnergyContainer.getInputAmperage();
            if (voltage > 0 && amperage > 0) {
                maxRecipe = Math.max(maxRecipe, voltage);
                minRecipe = Math.min(minRecipe, voltage);
                input += voltage * amperage;
                count++;
            } else {
                output += iEnergyContainer.getOutputVoltage() * iEnergyContainer.getOutputAmperage();
            }
        }
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            if (iEnergyContainer.getInputVoltage() == maxRecipe) maxCount++;
        }
        long recipe;
        if (dcpMode == 1) {
            recipe = maxCount > 1 ? maxRecipe << 2 : maxRecipe;
        } else if (dcpMode == 2) {
            recipe = maxRecipe == minRecipe && maxCount > 1 ? maxRecipe << 2 : maxRecipe;
        } else {
            recipe = count > 1 ? minRecipe << 2 : minRecipe;
        }
        this.capacity = capacity;
        this.inputVoltage = input;
        this.outputVoltage = output;
        this.inputAmperage = inputVoltage > 0 ? 1 : 0;
        this.outputAmperage = outputVoltage > 0 ? 1 : 0;
        if (recipe == 0 && outputAmperage > 0) {
            recipe = outputVoltage;
            overclockVoltage = outputVoltage;
        } else {
            overclockVoltage = inputVoltage;
        }
        maxVoltage = Math.min(GTValues.V[GTValues.MAX], recipe);
    }

    @Override
    public long acceptEnergyFromNetwork(Object o, Direction side, long voltage, long energyToAdd) {
        long energyAdded = 0L;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            energyAdded += iEnergyContainer.acceptEnergyFromNetwork(o, null, voltage, energyToAdd - energyAdded);
            if (energyAdded == energyToAdd) {
                return energyAdded;
            }
        }
        return energyAdded;
    }

    @Override
    public long changeEnergy(long energyToAdd) {
        long energyAdded = 0L;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            energyAdded += iEnergyContainer.changeEnergy(energyToAdd - energyAdded);
            if (energyAdded == energyToAdd) {
                return energyAdded;
            }
        }
        return energyAdded;
    }

    @Override
    public long getEnergyStored() {
        long energyStored = 0L;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            energyStored += iEnergyContainer.getEnergyStored();
        }
        return energyStored;
    }

    @Override
    public long getEnergyCapacity() {
        return capacity;
    }

    @Override
    public boolean inputsEnergy(Direction side) {
        return false;
    }

    @Override
    public long getInputPerSec() {
        long sum = 0;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            sum += iEnergyContainer.getInputPerSec();
        }
        return sum;
    }

    @Override
    public long getOutputPerSec() {
        long sum = 0;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            sum += iEnergyContainer.getOutputPerSec();
        }
        return sum;
    }

    public long getInputVoltage() {
        return this.inputVoltage;
    }

    public long getOutputVoltage() {
        return this.outputVoltage;
    }

    public long getInputAmperage() {
        return this.inputAmperage;
    }

    public long getOutputAmperage() {
        return this.outputAmperage;
    }

    public long getOverclockVoltage() {
        return this.overclockVoltage;
    }

    public long getMaxVoltage() {
        return this.maxVoltage;
    }
}
