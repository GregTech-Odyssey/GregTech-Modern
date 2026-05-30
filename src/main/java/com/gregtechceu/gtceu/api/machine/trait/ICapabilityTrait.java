package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.recipe.handler.IO;

import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public interface ICapabilityTrait {

    IO getCapabilityIO();

    Predicate<Direction> getCapabilityValidator();

    default boolean hasCapability(@Nullable Direction side) {
        return getCapabilityIO() != IO.NONE && getCapabilityValidator().test(side);
    }

    default boolean canCapInput() {
        return getCapabilityIO().support(IO.IN);
    }

    default boolean canCapOutput() {
        return getCapabilityIO().support(IO.OUT);
    }
}
