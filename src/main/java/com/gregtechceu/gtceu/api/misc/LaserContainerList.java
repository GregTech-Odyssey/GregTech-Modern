package com.gregtechceu.gtceu.api.misc;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.ILaserContainer;

import net.minecraft.core.Direction;

import java.util.List;

public class LaserContainerList implements ILaserContainer {

    private final ILaserContainer[] energyContainerList;

    public LaserContainerList(List<? extends ILaserContainer> energyContainerList) {
        this.energyContainerList = energyContainerList.toArray(new ILaserContainer[0]);
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
        for (ILaserContainer iEnergyContainer : energyContainerList) {
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
        for (ILaserContainer iEnergyContainer : energyContainerList) {
            energyStored += iEnergyContainer.getEnergyStored();
        }
        return energyStored;
    }

    @Override
    public long getEnergyCapacity() {
        long energyCapacity = 0L;
        for (ILaserContainer iEnergyContainer : energyContainerList) {
            energyCapacity += iEnergyContainer.getEnergyCapacity();
        }
        return energyCapacity;
    }

    @Override
    public long getInputAmperage() {
        return 1L;
    }

    @Override
    public long getOutputAmperage() {
        return 1L;
    }

    @Override
    public long getInputVoltage() {
        long inputVoltage = 0L;
        for (ILaserContainer container : energyContainerList) {
            inputVoltage += container.getInputVoltage() * container.getInputAmperage();
        }
        return inputVoltage;
    }

    @Override
    public long getOutputVoltage() {
        long outputVoltage = 0L;
        for (ILaserContainer container : energyContainerList) {
            outputVoltage += container.getOutputVoltage() * container.getOutputAmperage();
        }
        return outputVoltage;
    }

    @Override
    public boolean inputsEnergy(Direction side) {
        return true;
    }

    @Override
    public boolean outputsEnergy(Direction side) {
        return true;
    }
}
