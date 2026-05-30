package com.gregtechceu.gtceu.api.transfer.item;

import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

import org.jetbrains.annotations.NotNull;

/**
 * An extension of {@link CombinedInvWrapper} that accounts for and inserts/extracts
 * past the capability IO of any {@link NotifiableItemStackHandler}s in its handler list.
 */
public class NotifiableAccountedInvWrapper extends CombinedInvWrapper {

    public NotifiableAccountedInvWrapper(ICustomItemStackHandler... handlers) {
        super(handlers);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        int index = getIndexForSlot(slot);
        var handler = getHandlerFromIndex(index);
        slot = getSlotFromIndex(slot, index);
        if (handler instanceof ICustomItemStackHandler noti) return noti.insertItemInternal(slot, stack, simulate);
        return handler.insertItem(slot, stack, simulate);
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        int index = getIndexForSlot(slot);
        var handler = getHandlerFromIndex(index);
        slot = getSlotFromIndex(slot, index);
        if (handler instanceof ICustomItemStackHandler noti) return noti.extractItemInternal(slot, amount, simulate);
        return handler.extractItem(slot, amount, simulate);
    }
}
