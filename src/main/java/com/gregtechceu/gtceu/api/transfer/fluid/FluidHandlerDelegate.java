package com.gregtechceu.gtceu.api.transfer.fluid;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraftforge.fluids.FluidStack;

import lombok.Setter;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.ParametersAreNonnullByDefault;

@Setter
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public abstract class FluidHandlerDelegate implements ICustomFluidStackHandler {

    public ICustomFluidStackHandler delegate;

    public FluidHandlerDelegate(ICustomFluidStackHandler delegate) {
        this.delegate = delegate;
    }

    //////////////////////////////////////
    // ****** OVERRIDE THESE ******//
    //////////////////////////////////////
    @Override
    public int getTanks() {
        return delegate.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return delegate.getFluidInTank(tank);
    }

    @Override
    @ApiStatus.Internal
    public void setFluidInTank(int tank, FluidStack fluidStack) {
        delegate.setFluidInTank(tank, fluidStack);
    }

    @Override
    public int getTankCapacity(int tank) {
        return delegate.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return delegate.isFluidValid(tank, stack);
    }

    @Override
    @ApiStatus.Internal
    public int fill(FluidStack resource, FluidAction action) {
        return delegate.fill(resource, action);
    }

    @Override
    public boolean supportsFill(int tank) {
        return delegate.supportsFill(tank);
    }

    @Override
    @ApiStatus.Internal
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return delegate.drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return delegate.drain(maxDrain, action);
    }

    @Override
    public boolean supportsDrain(int tank) {
        return delegate.supportsDrain(tank);
    }
}
