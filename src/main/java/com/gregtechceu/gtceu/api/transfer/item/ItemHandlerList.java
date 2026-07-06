package com.gregtechceu.gtceu.api.transfer.item;

import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import org.jetbrains.annotations.NotNull;

public class ItemHandlerList implements ICustomItemStackHandler {

    protected final ICustomItemStackHandler[] handlers;

    public ItemHandlerList(ICustomItemStackHandler... handlers) {
        this.handlers = handlers;
    }

    @Override
    public int getSlots() {
        // computed dynamically: delegates (e.g. ItemHandlerProxyTrait) may change their slot
        // count after this wrapper is created, such as when a multiblock forms after chunk load
        int size = 0;
        for (var handler : handlers) {
            size += handler.getSlots();
        }
        return size;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        for (var handler : handlers) {
            var slots = handler.getSlots();
            if (slot < slots) {
                return handler.getStackInSlot(slot);
            }
            slot -= slots;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        for (var handler : handlers) {
            var slots = handler.getSlots();
            if (slot < slots) {
                handler.setStackInSlot(slot, stack);
                return;
            }
            slot -= slots;
        }
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        for (var handler : handlers) {
            var slots = handler.getSlots();
            if (slot < slots) {
                return handler.insertItem(slot, stack, simulate);
            }
            slot -= slots;
        }
        return stack;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        for (var handler : handlers) {
            var slots = handler.getSlots();
            if (slot < slots) {
                return handler.extractItem(slot, amount, simulate);
            }
            slot -= slots;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItemInternal(int slot, @NotNull ItemStack stack, boolean simulate) {
        for (var handler : handlers) {
            var slots = handler.getSlots();
            if (slot < slots) {
                return handler.insertItemInternal(slot, stack, simulate);
            }
            slot -= slots;
        }
        return stack;
    }

    @Override
    public ItemStack extractItemInternal(int slot, int amount, boolean simulate) {
        for (var handler : handlers) {
            var slots = handler.getSlots();
            if (slot < slots) {
                return handler.extractItemInternal(slot, amount, simulate);
            }
            slot -= slots;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int insertExternal(AEItemKey itemKey, int amount, Actionable mode) {
        var totalInserted = 0;
        for (var handler : handlers) {
            var inserted = handler.insertExternal(itemKey, amount, mode);
            if (inserted > 0) {
                totalInserted += inserted;
                amount -= inserted;
                if (amount <= 0) break;
            }
        }
        return totalInserted;
    }

    @Override
    public int extractExternal(AEItemKey itemKey, int amount, Actionable mode) {
        var totalExtracted = 0;
        for (var handler : handlers) {
            var extracted = handler.extractExternal(itemKey, amount, mode);
            if (extracted > 0) {
                totalExtracted += extracted;
                amount -= extracted;
                if (amount <= 0) break;
            }
        }
        return totalExtracted;
    }

    @Override
    public int getSlotLimit(int slot) {
        for (var handler : handlers) {
            var slots = handler.getSlots();
            if (slot < slots) {
                return handler.getSlotLimit(slot);
            }
            slot -= slots;
        }
        return 0;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        for (var handler : handlers) {
            var slots = handler.getSlots();
            if (slot < slots) {
                return handler.isItemValid(slot, stack);
            }
            slot -= slots;
        }
        return false;
    }
}
