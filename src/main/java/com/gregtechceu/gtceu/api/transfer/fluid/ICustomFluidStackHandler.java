package com.gregtechceu.gtceu.api.transfer.fluid;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.NotNull;

public interface ICustomFluidStackHandler extends IFluidHandler {

    ICustomFluidStackHandler EMPTY = new ICustomFluidStackHandler() {

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

    default int fillInternal(FluidStack resource, FluidAction action) {
        return fill(resource, action);
    }

    default FluidStack drainInternal(FluidStack resource, FluidAction action) {
        return drain(resource, action);
    }

    default boolean supportsFill(int tank) {
        return true;
    }

    default boolean supportsDrain(int tank) {
        return true;
    }

    static FluidStack copy(FluidStack stack, int amount) {
        if (amount < 1) return FluidStack.EMPTY;
        var copy = stack.copy();
        copy.setAmount(amount);
        return copy;
    }
}
