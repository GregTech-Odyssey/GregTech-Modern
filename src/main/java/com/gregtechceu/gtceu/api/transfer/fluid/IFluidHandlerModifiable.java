package com.gregtechceu.gtceu.api.transfer.fluid;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.NotNull;

public interface IFluidHandlerModifiable extends IFluidHandler {

    IFluidHandlerModifiable EMPTY = new IFluidHandlerModifiable() {

        @Override
        public void setFluidInTank(int tank, FluidStack stack) {}

        public int getTanks() {
            return 1;
        }

        public @NotNull FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        public int getTankCapacity(int tank) {
            return 0;
        }

        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return true;
        }

        public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
            return 0;
        }

        public @NotNull FluidStack drain(FluidStack resource, IFluidHandler.FluidAction action) {
            return FluidStack.EMPTY;
        }

        public @NotNull FluidStack drain(int maxDrain, IFluidHandler.FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    void setFluidInTank(int tank, FluidStack stack);

    default boolean supportsFill(int tank) {
        return true;
    }

    default boolean supportsDrain(int tank) {
        return true;
    }
}
