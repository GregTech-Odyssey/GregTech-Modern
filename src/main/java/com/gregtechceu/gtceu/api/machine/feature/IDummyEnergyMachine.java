package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;

import net.minecraft.core.Direction;

public interface IDummyEnergyMachine extends IElectricMachine {

    default boolean jade() {
        return true;
    }

    class DummyContainer implements IEnergyContainer {

        private final long eut;

        public DummyContainer(long eut) {
            this.eut = eut;
        }

        @Override
        public long acceptEnergyFromNetwork(Object o, Direction side, long voltage, long amperage) {
            return 0;
        }

        @Override
        public boolean inputsEnergy(Direction side) {
            return false;
        }

        @Override
        public long changeEnergy(long differenceAmount) {
            return differenceAmount;
        }

        @Override
        public long getEnergyStored() {
            return eut;
        }

        @Override
        public long getEnergyCapacity() {
            return eut;
        }

        @Override
        public long getInputAmperage() {
            return 0;
        }

        @Override
        public long getInputVoltage() {
            return 0;
        }
    }
}
