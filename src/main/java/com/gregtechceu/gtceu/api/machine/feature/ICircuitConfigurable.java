package com.gregtechceu.gtceu.api.machine.feature;

/**
 * Machines that expose a programmable integrated circuit slot for config copy tools.
 * The meta machine config copy card only reads/writes through this interface.
 */
public interface ICircuitConfigurable extends IMachineFeature {

    /** Stored / returned when the circuit slot is empty. */
    int CIRCUIT_EMPTY = -1;

    /**
     * Whether this machine currently has a circuit slot that can be copied.
     * e.g. import buses/hatches may return false when used as export parts.
     */
    default boolean hasCircuitConfig() {
        return true;
    }

    /**
     * @return circuit configuration (0–32), or {@link #CIRCUIT_EMPTY} if empty
     */
    int getCircuitConfiguration();

    /**
     * Set circuit configuration. Pass {@link #CIRCUIT_EMPTY} (or any negative) to clear the slot.
     */
    void setCircuitConfiguration(int configuration);
}
