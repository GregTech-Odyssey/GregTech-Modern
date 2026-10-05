package com.gregtechceu.gtceu.api.transfer.forge;

import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import org.jetbrains.annotations.Nullable;

public final class ForgeItemView implements IKeyHandler<AEItemKey> {

    private static final int[] NO_SLOTS = new int[0];

    private final IItemHandler handler;
    private int[] empties = NO_SLOTS;

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

    private static boolean holds(ItemStack stack, AEItemKey key) {
        if (stack.getItem() != key.getItem()) return false;
        var t = stack.getTag();
        var kt = key.getTag();
        return t == kt || (t == null || t.isEmpty() ? kt == null : t.equals(kt));
    }

    private static long insertSlot(IItemHandler h, int slot, AEItemKey key, long amount, boolean simulate) {
        int n = Keys.saturatedInt(Math.min(amount, Math.max(key.getMaxStackSize(), h.getSlotLimit(slot))));
        return n - h.insertItem(slot, key.toStack(n), simulate).getCount();
    }

    private static long extractSlot(IItemHandler h, int slot, long amount, boolean simulate) {
        long total = 0;
        while (total < amount) {
            int c = h.extractItem(slot, Keys.saturatedInt(amount - total), simulate).getCount();
            if (c <= 0) break;
            total += c;
            if (simulate) break;
        }
        return total;
    }

    @Override
    public AEKeyType keyType() {
        return AEKeyTypes.ITEMS;
    }

    @Override
    public int size() {
        return handler.getSlots();
    }

    @Override
    public @Nullable AEItemKey keyAt(int slot) {
        return AEItemKey.of(handler.getStackInSlot(slot));
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
        return insertSlot(handler, slot, key, amount, simulate);
    }

    @Override
    public long extract(int slot, AEItemKey key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        var h = handler;
        return holds(h.getStackInSlot(slot), key) ? extractSlot(h, slot, amount, simulate) : 0;
    }

    @Override
    public long insert(AEItemKey key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        var h = handler;
        int size = h.getSlots();
        var e = empties;
        if (e.length < size) empties = e = new int[size];
        int ne = 0;
        long left = amount;
        for (int i = 0; i < size; i++) {
            var stack = h.getStackInSlot(i);
            if (stack.isEmpty()) {
                e[ne++] = i;
            } else if (holds(stack, key)) {
                left -= insertSlot(h, i, key, left, simulate);
                if (left <= 0) return amount;
            }
        }
        for (int j = 0; j < ne; j++) {
            left -= insertSlot(h, e[j], key, left, simulate);
            if (left <= 0) return amount;
        }
        return amount - left;
    }

    @Override
    public long extract(AEItemKey key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        var h = handler;
        int size = h.getSlots();
        long left = amount;
        for (int i = 0; i < size; i++) {
            if (holds(h.getStackInSlot(i), key)) {
                left -= extractSlot(h, i, left, simulate);
                if (left <= 0) break;
            }
        }
        return amount - left;
    }

    @Override
    public long count(AEItemKey key) {
        var h = handler;
        int size = h.getSlots();
        long total = 0;
        for (int i = 0; i < size; i++) {
            var stack = h.getStackInSlot(i);
            if (holds(stack, key)) total = Keys.add(total, stack.getCount());
        }
        return total;
    }
}
