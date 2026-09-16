package com.gregtechceu.gtceu.api.transfer.item;

import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import org.jetbrains.annotations.NotNull;

public class LockableItemStackHandler implements ICustomItemStackHandler {

    protected final ICustomItemStackHandler delegate;
    protected boolean lock;

    public LockableItemStackHandler(ICustomItemStackHandler delegate) {
        this.delegate = delegate;
    }

    public LockableItemStackHandler setLock(boolean lock) {
        this.lock = lock;
        return this;
    }

    @Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(slot);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        return lock ? stack : delegate.insertItem(slot, stack, simulate);
    }

    @Override
    public @NotNull ItemStack insertItemStacked(@NotNull ItemStack stack, boolean simulate) {
        return lock ? stack : delegate.insertItemStacked(stack, simulate);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        return lock ? ItemStack.EMPTY : delegate.extractItem(slot, amount, simulate);
    }

    @Override
    public ItemStack insertItemInternal(int slot, @NotNull ItemStack stack, boolean simulate) {
        return lock ? stack : delegate.insertItemInternal(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItemInternal(int slot, int amount, boolean simulate) {
        return lock ? ItemStack.EMPTY : delegate.extractItemInternal(slot, amount, simulate);
    }

    @Override
    public int insertExternal(AEItemKey itemKey, int amount, Actionable mode) {
        return lock ? 0 : delegate.insertExternal(itemKey, amount, mode);
    }

    @Override
    public int extractExternal(AEItemKey itemKey, int amount, Actionable mode) {
        return lock ? 0 : delegate.extractExternal(itemKey, amount, mode);
    }

    @Override
    public int getSlotLimit(int slot) {
        return delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return !lock && delegate.isItemValid(slot, stack);
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (!lock) {
            delegate.setStackInSlot(slot, stack);
        }
    }
}
