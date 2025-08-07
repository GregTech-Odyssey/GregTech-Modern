package com.gregtechceu.gtceu.api.transfer.fluid;

import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

public class LockableIFluidHandler implements IFluidHandlerModifiable {

    protected final IFluidHandlerModifiable delegate;
    protected boolean lock;

    public LockableIFluidHandler(IFluidHandlerModifiable delegate) {
        this.delegate = delegate;
    }

    public LockableIFluidHandler setLock(boolean lock) {
        this.lock = lock;
        return this;
    }

    @Override
    public int getTanks() {
        return delegate.getTanks();
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        return delegate.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return delegate.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return !lock && delegate.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return lock ? 0 : delegate.fill(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
        return lock ? FluidStack.EMPTY : delegate.drain(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        return lock ? FluidStack.EMPTY : delegate.drain(maxDrain, action);
    }

    @Override
    public void setFluidInTank(int tank, FluidStack stack) {
        if (!lock) {
            delegate.setFluidInTank(tank, stack);
        }
    }
}
