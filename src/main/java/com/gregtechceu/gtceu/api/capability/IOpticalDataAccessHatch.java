package com.gregtechceu.gtceu.api.capability;

import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.machine.feature.IMachineFeature;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IOpticalDataAccessHatch extends IDataAccessHatch, IMachineFeature {

    /**
     * @return if this hatch transmits data through cables
     */
    boolean isTransmitter();

    @Override
    default @Nullable <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == GTCapability.CAPABILITY_DATA_ACCESS) {
            return GTCapability.CAPABILITY_DATA_ACCESS.orEmpty(cap, LazyOptional.of(() -> this));

        }
        return null;
    }
}
