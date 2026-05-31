package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;

import org.jetbrains.annotations.NotNull;

public interface IComputationContainerMachine {

    @NotNull
    IOpticalComputationProvider getComputationProvider();

    default long requestCWU(long cwut, boolean simulate) {
        return getComputationProvider().requestCWU(cwut, simulate);
    }
}
