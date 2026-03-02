package com.gregtechceu.gtceu.api.capability;

import net.minecraft.core.Direction;

import java.math.BigInteger;

public interface IEnergyContainer extends IEnergyInfoProvider {

    /**
     * This method is basically {@link #changeEnergy(long)}.
     * This method should always be used when energy is passed between blocks.
     * 
     * @return amount of energy added
     */
    long acceptEnergyFromNetwork(Object sender, Direction side, long voltage, long energyToAdd);

    /**
     * @return if this container accepts energy from the given side
     */
    boolean inputsEnergy(Direction side);

    /**
     * @return if this container can output energy to the given side
     */
    default boolean outputsEnergy(Direction side) {
        return false;
    }

    /**
     * This changes the amount stored.
     * <b>This should only be used internally</b> (f.e. draining while working or filling while generating).
     * For transfer between blocks use {@link #acceptEnergyFromNetwork(Object, Direction, long, long)}!!!
     *
     * @param differenceAmount amount of energy to add (>0) or remove (<0)
     * @return amount of energy added or removed
     */
    long changeEnergy(long differenceAmount);

    /**
     * Adds specified amount of energy to this energy container
     *
     * @param energyToAdd amount of energy to add
     * @return amount of energy added
     */
    default long addEnergy(long energyToAdd) {
        return changeEnergy(energyToAdd);
    }

    /**
     * Removes specified amount of energy from this energy container
     *
     * @param energyToRemove amount of energy to remove
     * @return amount of energy removed
     */
    default long removeEnergy(long energyToRemove) {
        return -changeEnergy(-energyToRemove);
    }

    /**
     * @return the maximum amount of energy that can be inserted
     */
    default long getEnergyCanBeInserted() {
        return getEnergyCapacity() - getEnergyStored();
    }

    /**
     * @return amount of currently stored energy
     */
    long getEnergyStored();

    /**
     * @return maximum amount of storable energy
     */
    long getEnergyCapacity();

    @Override
    default EnergyInfo getEnergyInfo() {
        return new EnergyInfo(BigInteger.valueOf(getEnergyCapacity()), BigInteger.valueOf(getEnergyStored()));
    }

    @Override
    default boolean supportsBigIntEnergyValues() {
        return false;
    }

    /**
     * @return maximum amount of outputable energy packets per tick
     */
    default long getOutputAmperage() {
        return 0L;
    }

    /**
     * @return output energy packet size
     */
    default long getOutputVoltage() {
        return 0L;
    }

    /**
     * @return maximum amount of receivable energy packets per tick
     */
    long getInputAmperage();

    /**
     * @return output energy packet size
     *         Overflowing this value will explode machine.
     */
    long getInputVoltage();

    /**
     * @return input eu/s
     */
    @Override
    default long getInputPerSec() {
        return 0L;
    }

    /**
     * @return output eu/s
     */
    @Override
    default long getOutputPerSec() {
        return 0L;
    }

    IEnergyContainer DEFAULT = ILaserContainer.DEFAULT;

    interface IDummyContainer extends IEnergyContainer {

        @Override
        default long acceptEnergyFromNetwork(Object o, Direction side, long voltage, long amperage) {
            return 0;
        }

        @Override
        default boolean inputsEnergy(Direction side) {
            return false;
        }

        @Override
        default long changeEnergy(long differenceAmount) {
            return differenceAmount;
        }

        @Override
        default long getInputAmperage() {
            return 0;
        }

        @Override
        default long getInputVoltage() {
            return 0;
        }
    }

    class InfiniteContainer implements IDummyContainer {

        protected final long eu;

        public InfiniteContainer(long eu) {
            this.eu = eu;
        }

        @Override
        public long getEnergyStored() {
            return eu;
        }

        @Override
        public long getEnergyCapacity() {
            return eu;
        }
    }
}
