package com.gregtechceu.gtceu.api.capability;

public interface IOpticalComputationProvider {

    long requestCWU(long cwu, boolean simulate);

    long getMaxCWU();

    default boolean canBridge() {
        return true;
    }
}
