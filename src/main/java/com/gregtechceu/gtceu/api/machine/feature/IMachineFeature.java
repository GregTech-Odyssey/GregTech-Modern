package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IMachineFeature {

    MetaMachine self();

    default @Nullable <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return null;
    }

    @Nullable
    default <T> Object getGTCapability(@NotNull Class<T> cap, @Nullable Direction side) {
        return null;
    }
}
