package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public interface ICapabilityTrait {

    default IO getCapabilityIO() {
        return IO.BOTH;
    }

    default Predicate<Direction> getCapabilityValidator() {
        return GTUtil.FAVORABLE;
    }

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
