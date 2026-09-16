package com.gregtechceu.gtceu.api.transfer.item;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.me.storage.ExternalStorageFacade;
import org.jetbrains.annotations.NotNull;

public interface ICustomItemStackHandler extends IItemHandlerModifiable, ExternalStorageFacade.MEStorageItemHandler {

    ICustomItemStackHandler EMPTY = new ICustomItemStackHandler() {

        @Override
        public int insertExternal(AEItemKey aeItemKey, int i, Actionable actionable) {
            return 0;
        }

        @Override
        public int extractExternal(AEItemKey aeItemKey, int i, Actionable actionable) {
            return 0;
        }

        @Override
        public void setStackInSlot(int slot, @NotNull ItemStack stack) {}

        @Override
        public int getSlots() {
            return 0;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return false;
        }
    };

    default ItemStack insertItemInternal(int slot, @NotNull ItemStack stack, boolean simulate) {
        return insertItem(slot, stack, simulate);
    }

    default ItemStack extractItemInternal(int slot, int amount, boolean simulate) {
        return extractItem(slot, amount, simulate);
    }

    /**
     * Inserts a stack across this handler while preserving any handler-wide insertion rules during simulation.
     * Implementations with cross-slot constraints should override this method.
     *
     * @return the part of {@code stack} that was not inserted
     */
    @NotNull
    default ItemStack insertItemStacked(@NotNull ItemStack stack, boolean simulate) {
        return ItemHandlerHelper.insertItemStacked(this, stack, simulate);
    }

    @Override
    default int insertExternal(AEItemKey itemKey, int amount, Actionable mode) {
        final var orgInput = itemKey.toStack(amount);
        final var simulate = mode == Actionable.SIMULATE;
        var remaining = insertItemStacked(orgInput, simulate);
        return amount - remaining.getCount();
    }

    @Override
    default int extractExternal(AEItemKey itemKey, int amount, Actionable mode) {
        final var slotCount = this.getSlots();
        final var simulate = mode == Actionable.SIMULATE;
        var totalExtracted = 0;
        for (var i = 0; i < slotCount; i++) {
            if (itemKey.matches(this.getStackInSlot(i))) {
                final var extracted = this.extractItem(i, amount - totalExtracted, simulate).getCount();
                if (extracted > 0) {
                    totalExtracted += extracted;
                    if (amount == totalExtracted) {
                        break;
                    }
                }
            }
        }
        return totalExtracted;
    }
}
