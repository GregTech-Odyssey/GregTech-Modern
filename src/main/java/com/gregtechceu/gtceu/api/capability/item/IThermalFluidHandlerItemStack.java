package com.gregtechceu.gtceu.api.capability.item;

import com.gregtechceu.gtceu.api.fluids.FluidState;
import com.gregtechceu.gtceu.api.fluids.GTFluid;

import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * Interface for FluidHandlerItemStacks which handle GT's unique fluid mechanics
 */
public interface IThermalFluidHandlerItemStack {

    /**
     *
     * @param stack the {@link FluidStack} to check
     * @return whether the FluidStack can be used to fill this fluid container
     */
    default boolean canFillFluidType(FluidStack stack) {
        Fluid fluid = stack.getFluid();
        if (fluid instanceof GTFluid gtFluid) {
            FluidState fluidState = gtFluid.getState();
            if (fluidState == FluidState.PLASMA && !isPlasmaProof()) return false;
            return fluidState != FluidState.GAS || isGasProof();
        } else {
            return !fluid.getFluidType().isLighterThanAir() || isGasProof();
        }
    }

    /**
     * @return true if this fluid container allows gases, otherwise false
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean isGasProof();

    /**
     * @return true if this fluid container allows plasmas, otherwise false
     */
    boolean isPlasmaProof();
}
