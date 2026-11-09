package com.gregtechceu.gtceu.api.machine.feature;

/** Per-machine EU reserve. Mutations run on the machine thread and must mark the owner changed. */
public interface IBufferedEnergyMachine extends IElectricMachine {

    int ENERGY_BUFFER_TICKS = 10;

    long getEnergyBuffer();

    void setEnergyBuffer(long energy);

    default long getEnergyBufferCapacity() {
        return bufferCapacity(getEnergyContainer().getInputVoltage());
    }

    static long bufferCapacity(long voltage) {
        return voltage > Long.MAX_VALUE / ENERGY_BUFFER_TICKS ? Long.MAX_VALUE : voltage * ENERGY_BUFFER_TICKS;
    }

    /** Includes the reserve without overflowing when the supply is also full. */
    default long getEnergyAvailable() {
        long stored = getEnergyContainer().getEnergyStored();
        long buffer = isEnergyBufferEnabled() ? getEnergyBuffer() : 0;
        return stored > Long.MAX_VALUE - buffer ? Long.MAX_VALUE : stored + buffer;
    }

    default long getEnergyBufferDisplayCapacity() {
        return Math.max(getEnergyBuffer(), getEnergyBufferCapacity());
    }

    @Override
    default boolean useEnergy(long eu, boolean simulate) {
        if (eu < 0) return eu != Long.MIN_VALUE && generateEnergy(-eu, simulate);
        if (!isEnergyBufferEnabled()) return IElectricMachine.super.useEnergy(eu, simulate);
        if (eu == 0) return true;
        long buffer = getEnergyBuffer();
        if (simulate) return buffer >= eu || getEnergyContainer().getEnergyStored() >= eu - buffer;
        if (buffer < eu) {
            long request = Math.max(eu, getEnergyBufferCapacity()) - buffer;
            long received = -getEnergyContainer().changeEnergy(-request);
            buffer += received;
            if (buffer < eu) {
                if (received != 0) setEnergyBuffer(buffer);
                return false;
            }
        }
        setEnergyBuffer(buffer - eu);
        return true;
    }
}
