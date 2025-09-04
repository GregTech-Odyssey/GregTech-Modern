package com.gregtechceu.gtceu.api.misc;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;

import net.minecraft.core.Direction;

import java.util.List;

public class EnergyContainerList implements IEnergyContainer {

    public static final EnergyContainerList EMPTY = new EnergyContainerList();

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
        long recipe = 0;
        int count = 0;
        long capacity = 0;
        for (IEnergyContainer iEnergyContainer : energyContainerList) {
            capacity += iEnergyContainer.getEnergyCapacity();
            long voltage = iEnergyContainer.getInputVoltage();
            long amperage = iEnergyContainer.getInputAmperage();
            if (voltage > 0 && amperage > 0) {
                if (recipe != 0 && recipe != voltage) {
                    count = -1;
                }
                recipe = Math.max(recipe, voltage);
                input += voltage * amperage;
                if (count < 0) continue;
                count++;
            } else {
                output += iEnergyContainer.getOutputVoltage() * iEnergyContainer.getOutputAmperage();
            }
        }
        if (count > 1) {
            recipe <<= 2;
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
