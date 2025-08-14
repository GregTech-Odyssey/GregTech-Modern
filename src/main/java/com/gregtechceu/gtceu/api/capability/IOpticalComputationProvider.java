package com.gregtechceu.gtceu.api.capability;

public interface IOpticalComputationProvider {

    long requestCWU(long cwu, boolean simulate);

    default long getMaxCWUt() {
        return requestCWU(Long.MAX_VALUE, true);
    }

    default boolean canBridge() {
        return true;
    }
}
