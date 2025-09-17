package com.gregtechceu.gtceu.api.transfer.fluid;

import com.gregtechceu.gtceu.api.misc.IContentChange;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class CustomFluidTank extends FluidTank implements IFluidHandlerModifiable, ITagSerializable<CompoundTag>, IContentChange {

    @NotNull
    protected Runnable onContentsChanged = GTUtil.NOOP;
    protected boolean freezeChanged = false;

    public CustomFluidTank(int capacity) {
        super(capacity, GTUtil.FAVORABLE);
    }

    public CustomFluidTank(int capacity, Predicate<FluidStack> validator) {
        super(capacity, validator);
    }

    public CustomFluidTank(FluidStack stack) {
        super(stack.getAmount());
        setFluid(stack);
    }

    @Override
    public void setFluidInTank(int tank, FluidStack stack) {
        setFluid(stack);
    }

    @Override
    public void setFluid(FluidStack stack) {
        super.setFluid(stack);
        this.onContentsChanged();
    }

    @Override
    public CompoundTag writeToNBT(CompoundTag nbt) {
        if (fluid.isEmpty()) return nbt;
        fluid.writeToNBT(nbt);
        return nbt;
    }

    @Override
    public CompoundTag serializeNBT() {
        return writeToNBT(new CompoundTag());
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        readFromNBT(nbt);
    }

    @Override
    public void onContentsChanged() {
        onContentsChanged.run();
    }

    @NotNull
    @Override
    public Runnable getOnContentsChanged() {
        return this.onContentsChanged;
    }

    @Override
    public void setOnContentsChanged(@NotNull final Runnable onContentsChanged) {
        if (freezeChanged) return;
        this.onContentsChanged = onContentsChanged;
    }

    public void setOnContentsChangedAndfreeze(@NotNull final Runnable onContentsChanged) {
        this.onContentsChanged = onContentsChanged;
        freezeChanged = true;
    }

    @Override
    public boolean isFreezeChanged() {
        return freezeChanged;
    }
}
