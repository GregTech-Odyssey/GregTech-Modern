package com.gregtechceu.gtceu.api.capability.recipe.function;

import net.minecraftforge.fluids.FluidStack;

@FunctionalInterface
public interface FluidPredicate {

    boolean test(FluidStack fluidStack, long amount);
}
