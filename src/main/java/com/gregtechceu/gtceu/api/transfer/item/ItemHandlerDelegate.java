package com.gregtechceu.gtceu.api.transfer.item;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public abstract class ItemHandlerDelegate implements ICustomItemStackHandler {

    public ICustomItemStackHandler delegate;

    public ItemHandlerDelegate(ICustomItemStackHandler delegate) {
        this.delegate = delegate;
    }

    protected void setDelegate(ICustomItemStackHandler delegate) {
        this.delegate = delegate;
    }

    //////////////////////////////////////
    // ****** OVERRIDE THESE ******//
    //////////////////////////////////////

    @Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        delegate.setStackInSlot(slot, stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return delegate.insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack insertItemStacked(ItemStack stack, boolean simulate) {
        return ICustomItemStackHandler.super.insertItemStacked(stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return delegate.extractItem(slot, amount, simulate);
    }

    @Override
    public ItemStack insertItemInternal(int slot, @NotNull ItemStack stack, boolean simulate) {
        return delegate.insertItemInternal(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItemInternal(int slot, int amount, boolean simulate) {
        return delegate.extractItemInternal(slot, amount, simulate);
    }

    @Override
    public int insertExternal(AEItemKey itemKey, int amount, Actionable mode) {
        return delegate.insertExternal(itemKey, amount, mode);
    }

    @Override
    public int extractExternal(AEItemKey itemKey, int amount, Actionable mode) {
        return delegate.extractExternal(itemKey, amount, mode);
    }

    @Override
    public int getSlotLimit(int slot) {
        return delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return delegate.isItemValid(slot, stack);
    }
}
