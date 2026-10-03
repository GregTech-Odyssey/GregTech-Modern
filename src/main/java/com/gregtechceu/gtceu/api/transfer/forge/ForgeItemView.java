package com.gregtechceu.gtceu.api.transfer.forge;

import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import net.minecraftforge.items.IItemHandler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import org.jetbrains.annotations.Nullable;

public final class ForgeItemView implements IKeyHandler<AEItemKey> {

    private final IItemHandler handler;

    private ForgeItemView(IItemHandler handler) {
        this.handler = handler;
    }

    public static IKeyHandler<AEItemKey> of(IItemHandler handler) {
        if (handler instanceof ForgeItemAdapter adapter) return adapter.getHandler();
        return new ForgeItemView(handler);
    }

    public IItemHandler getHandler() {
        return handler;
    }

    @Override
    public AEKeyType keyType() {
        return AEKeyType.items();
    }

    @Override
    public int size() {
        return handler.getSlots();
    }

    @Override
    public @Nullable AEItemKey keyAt(int slot) {
        return Keys.item(handler.getStackInSlot(slot));
    }

    @Override
    public long amountAt(int slot) {
        return handler.getStackInSlot(slot).getCount();
    }

    @Override
    public long slotLimit(int slot) {
        return handler.getSlotLimit(slot);
    }

    @Override
    public long insert(int slot, AEItemKey key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        int n = Keys.saturatedInt(Math.min(amount, Math.max(key.getMaxStackSize(), handler.getSlotLimit(slot))));
        var rest = handler.insertItem(slot, key.toStack(n), simulate);
        return n - rest.getCount();
    }

    @Override
    public long extract(int slot, AEItemKey key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        var stack = handler.getStackInSlot(slot);
        if (stack.isEmpty() || Keys.itemType(stack) != key) return 0;
        long total = 0;
        while (total < amount) {
            var got = handler.extractItem(slot, Keys.saturatedInt(amount - total), simulate);
            int c = got.getCount();
            if (c <= 0) break;
            total += c;
            if (simulate) break;
        }
        return total;
    }

    @Override
    public long insert(AEItemKey key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        long left = amount;
        int size = handler.getSlots();
        for (int i = 0; i < size && left > 0; i++) {
            var stack = handler.getStackInSlot(i);
            if (!stack.isEmpty() && key.matches(stack)) left -= insert(i, key, left, simulate);
        }
        for (int i = 0; i < size && left > 0; i++) {
            if (handler.getStackInSlot(i).isEmpty()) left -= insert(i, key, left, simulate);
        }
        return amount - left;
    }

    @Override
    public long extract(AEItemKey key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        long left = amount;
        int size = handler.getSlots();
        for (int i = 0; i < size && left > 0; i++) {
            left -= extract(i, key, left, simulate);
        }
        return amount - left;
    }
}
