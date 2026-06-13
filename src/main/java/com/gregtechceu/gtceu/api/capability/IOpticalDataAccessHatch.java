package com.gregtechceu.gtceu.api.capability;

import com.gregtechceu.gtceu.api.machine.feature.IMachineFeature;

import net.minecraft.core.Direction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IOpticalDataAccessHatch extends IDataAccessHatch, IMachineFeature {

    /**
     * @return if this hatch transmits data through cables
     */
    boolean isTransmitter();

    boolean testCapability(@Nullable Direction side);

    @Override
    default @Nullable <T> Object getGTCapability(@NotNull Class<T> cap, @Nullable Direction side) {
        if (cap == GTCapability.DATA_ACCESS) {
            if (testCapability(side)) return this;
            return GTCapability.EMPTY;
        }
        return null;
    }
}
