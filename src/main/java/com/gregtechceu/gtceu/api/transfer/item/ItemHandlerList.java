package com.gregtechceu.gtceu.api.transfer.item;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;

public class ItemHandlerList implements IItemHandlerModifiable {

    protected final IItemHandler[] handlers;
    protected final int size;

    public ItemHandlerList(IItemHandler... handlers) {
        this.handlers = handlers;
        int size = 0;
        for (IItemHandler handler : handlers) {
            size += handler.getSlots();
        }
        this.size = size;
    }

    @Override
    public int getSlots() {
        return size;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        for (IItemHandler handler : handlers) {
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
        for (IItemHandler handler : handlers) {
            var slots = handler.getSlots();
            if (slot < slots) {
                if (handler instanceof IItemHandlerModifiable modifiable) modifiable.setStackInSlot(slot, stack);
                return;
            }
            slot -= slots;
        }
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        for (IItemHandler handler : handlers) {
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
        for (IItemHandler handler : handlers) {
            var slots = handler.getSlots();
            if (slot < slots) {
                return handler.extractItem(slot, amount, simulate);
            }
            slot -= slots;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        for (IItemHandler handler : handlers) {
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
        for (IItemHandler handler : handlers) {
            var slots = handler.getSlots();
            if (slot < slots) {
                return handler.isItemValid(slot, stack);
            }
            slot -= slots;
        }
        return false;
    }
}
