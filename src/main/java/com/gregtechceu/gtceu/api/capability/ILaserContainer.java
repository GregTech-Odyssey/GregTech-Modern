package com.gregtechceu.gtceu.api.capability;

import net.minecraft.core.Direction;

/**
 * It is its own separate interface to make piping work easier
 */
public interface ILaserContainer extends IEnergyContainer {

    ILaserContainer DEFAULT = new ILaserContainer() {

        @Override
        public long acceptEnergyFromNetwork(Direction Direction, long l, long l1) {
            return 0;
        }

        @Override
        public boolean inputsEnergy(Direction Direction) {
            return false;
        }

        @Override
        public long changeEnergy(long l) {
            return 0;
        }

        @Override
        public long getEnergyStored() {
            return 0;
        }

        @Override
        public long getEnergyCapacity() {
            return 0;
        }

        @Override
        public long getInputAmperage() {
            return 0;
        }

        @Override
        public long getInputVoltage() {
            return 0;
        }
    };
}
