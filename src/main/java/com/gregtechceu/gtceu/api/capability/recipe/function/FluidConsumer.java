package com.gregtechceu.gtceu.api.capability.recipe.function;

import net.minecraftforge.fluids.FluidStack;

@FunctionalInterface
public interface FluidConsumer {

    void accept(FluidStack fluidStack, long amount);
}
