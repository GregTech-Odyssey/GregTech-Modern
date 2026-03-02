package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.utils.GTUtil;

import org.jetbrains.annotations.NotNull;

public interface IElectricMachine extends IMachineLife {

    @NotNull
    IEnergyContainer getEnergyContainer();

    default int getTier() {
        return GTUtil.getTierByVoltage(getEnergyContainer().getEnergyCapacity());
    }

    default boolean useEnergy(long eu, boolean simulated) {
        if (simulated) {
            return getEnergyContainer().getEnergyStored() >= eu;
        }
        eu = -eu;
        return getEnergyContainer().changeEnergy(eu) == eu;
    }

    default boolean generateEnergy(long eu, boolean simulated) {
        var container = getEnergyContainer();
        if (container.getEnergyCapacity() - container.getEnergyStored() >= eu) {
            if (!simulated) container.changeEnergy(eu);
            return true;
        }
        return false;
    }
}
