package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.block.ICoilType;

public interface ICoilMachine {

    default int getTemperature() {
        return getCoilType().getCoilTemperature();
    }

    default int getCoilTier() {
        return getCoilType().getTier();
    }

    ICoilType getCoilType();
}
