package com.gregtechceu.gtceu.api.machine.feature;

/** Per-machine EU reserve. Mutations run on the machine thread and must mark the owner changed. */
public interface IBufferedEnergyMachine extends IElectricMachine {

    int ENERGY_BUFFER_TICKS = 10;

    long getEnergyBuffer();

    long getEnergyBufferCapacity();

    static long bufferCapacity(long voltage) {
        return voltage > Long.MAX_VALUE / ENERGY_BUFFER_TICKS ? Long.MAX_VALUE : voltage * ENERGY_BUFFER_TICKS;
    }

    /** Includes the reserve without overflowing when the supply is also full. */
    default long getEnergyAvailable() {
        long stored = getEnergyContainer().getEnergyStored();
        long buffer = getEnergyBuffer();
        return stored > Long.MAX_VALUE - buffer ? Long.MAX_VALUE : stored + buffer;
    }

    default long getEnergyBufferDisplayCapacity() {
        return Math.max(getEnergyBuffer(), getEnergyBufferCapacity());
    }
}
