package com.gregtechceu.gtceu.api.transfer.forge;

import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FlatItemAdapter extends ForgeItemAdapter {

    private static final int REFUSES_OTHER = 8;
    private static final ItemStack[][] NO_CACHES = new ItemStack[0][];
    private static final ItemViews[] NO_STORES = new ItemViews[0];

    private final int size;
    @Nullable
    private final KeyInventory<?> inv0;
    @Nullable
    private final NotifiableInventory<?> owner0;
    private final int direct0;
    private final KeyInventory<?>[] invs;
    private final int[] locals;
    private final byte[] direct;
    private final NotifiableInventory<?>[] owners;
    private final KeyInventory<?>[] distinct;
    private final ItemStack[][] caches;
    private final ItemViews[] stores;
    private final int uniformLimit;
    @Nullable
    private ItemStack[] views;
    @Nullable
    private ItemViews store0;

    FlatItemAdapter(IKeyHandler<AEItemKey> handler, Flat f) {
        super(handler);
        int n = f.size;
        var d = f.direct;
        for (int s = 0; s < n; s++) {
            if (f.invs[s] != null && (strictSlots || (d[s] & INSERT) != 0)) d[s] |= REFUSES_OTHER;
        }
        this.size = n;
        this.inv0 = f.inv0;
        this.invs = f.invs;
        this.locals = f.locals;
        this.direct = f.direct;
        this.owners = f.owners;
        if (f.inv0 != null) {
            this.owner0 = f.owners[0];
            this.direct0 = f.direct[0];
            this.distinct = Flat.NO_INVS;
            this.caches = NO_CACHES;
            this.stores = NO_STORES;
        } else {
            this.owner0 = null;
            this.direct0 = 0;
            this.distinct = Flat.distinct(f.invs);
            this.caches = new ItemStack[n][];
            this.stores = new ItemViews[n];
        }
        this.uniformLimit = f.uniformLimit();
    }

    @Override
    public int getSlots() {
        return size;
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        if (inv0 != null) {
            var vs = views;
            if (vs != null) {
                var v = vs[slot];
                if (v != null) return v;
            }
        } else {
            var c = caches[slot];
            if (c != null) {
                var v = c[locals[slot]];
                if (v != null) return v;
            }
        }
        return refresh(slot);
    }

    private ItemStack refresh(int slot) {
        var inv = inv0;
        int l = slot;
        if (inv == null) {
            inv = invs[slot];
            l = locals[slot];
            if (inv == null) {
                var lo = local(size);
                lo.touch(slot);
                return handler.readSlot(slot, slot, lo);
            }
        }
        var st = bind(slot, inv);
        st.touch(l);
        long amount = inv.amountAt(l);
        var v = st.view(l, amount == 0 ? null : (AEItemKey) inv.rawKeyAt(l), amount);
        st.cache[l] = v;
        return v;
    }

    private ItemViews bind(int slot, KeyInventory<?> inv) {
        var st = inv0 != null ? store0 : stores[slot];
        if (st == null) {
            st = ItemViews.attach(inv);
            bindAll(inv, st);
        }
        return st;
    }

    private @Nullable ItemViews peek(int slot, KeyInventory<?> inv) {
        var st = inv0 != null ? store0 : stores[slot];
        if (st == null && (st = ItemViews.peek(inv)) != null) bindAll(inv, st);
        return st;
    }

    private void bindAll(KeyInventory<?> inv, ItemViews st) {
        if (inv0 != null) {
            store0 = st;
            views = st.cache;
            return;
        }
        var is = invs;
        for (int s = 0; s < is.length; s++) {
            if (is[s] == inv) {
                stores[s] = st;
                caches[s] = st.cache;
            }
        }
    }

    private void touchSlot(int slot) {
        var inv = inv0;
        int l = slot;
        if (inv == null) {
            inv = invs[slot];
            l = locals[slot];
        }
        if (inv != null) {
            var st = peek(slot, inv);
            if (st != null) st.touch(l);
        } else {
            var lo = local;
            if (lo != null) lo.touch(slot);
        }
    }

    @Override
    void touchKey(AEItemKey key) {
        var st = store0;
        if (st != null && local == null) st.touchKey(key);
        else touchKeys(key);
    }

    private void touchKeys(AEItemKey key) {
        var inv = inv0;
        if (inv != null) {
            var st = store0;
            if (st == null && (st = ItemViews.peek(inv)) != null) bindAll(inv, st);
            if (st != null) st.touchKey(key);
        } else {
            for (var d : distinct) {
                var st = ItemViews.peek(d);
                if (st != null) st.touchKey(key);
            }
        }
        var lo = local;
        if (lo != null) lo.touchKey(key);
    }

    private @Nullable AEItemKey keyAt(int slot) {
        var inv = inv0;
        int l = slot;
        if (inv == null) {
            inv = invs[slot];
            if (inv == null) return handler.keyAt(slot);
            l = locals[slot];
        }
        return inv.amountAt(l) == 0 ? null : (AEItemKey) inv.rawKeyAt(l);
    }

    private long amountAt(int slot) {
        var inv = inv0;
        if (inv != null) return inv.amountAt(slot);
        inv = invs[slot];
        return inv != null ? inv.amountAt(locals[slot]) : handler.amountAt(slot);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        int count = stack.getCount();
        if (count <= 0) return stack;
        var inv = inv0;
        if (inv != null) return insertAt(slot, inv, slot, direct0, owner0, stack, count, simulate);
        inv = invs[slot];
        return inv != null ? insertAt(slot, inv, locals[slot], direct[slot], owners[slot], stack, count, simulate) : insertForeign(slot, stack, count, simulate);
    }

    @SuppressWarnings("unchecked")
    private ItemStack insertAt(int slot, KeyInventory<?> inv, int l, int d, @Nullable NotifiableInventory<?> o, ItemStack stack, int count, boolean simulate) {
        var st = peek(slot, inv);
        if (st != null) st.touch(l);
        var held = (AEItemKey) inv.rawKeyAt(l);
        AEItemKey key;
        if (inv.amountAt(l) == 0) key = keyForEmpty(held, stack);
        else if (held.getItem() != stack.getItem() && (d & REFUSES_OTHER) != 0) return stack;
        else key = keyFor(held, stack);
        if (key == null) return stack;
        long n;
        if (direct(d, o)) {
            n = ((KeyInventory<AEItemKey>) inv).insert(l, key, count, simulate);
            if (n > 0 && !simulate && st != null) st.placed(inv, l, key);
        } else {
            n = insertGuarded(slot, key, count, d, o, simulate);
        }
        return rest(stack, count, n);
    }

    private static boolean direct(int d, @Nullable NotifiableInventory<?> o) {
        return (d & INSERT) != 0 && (o == null || !o.isVoiding && o.canCapInput());
    }

    private long insertGuarded(int slot, AEItemKey key, int count, int d, @Nullable NotifiableInventory<?> o, boolean simulate) {
        return (d & INSERT) == 0 || o.canCapInput() ? handler.insert(slot, key, count, simulate) : 0;
    }

    private ItemStack insertForeign(int slot, ItemStack stack, int count, boolean simulate) {
        var lo = local;
        if (lo != null) lo.touch(slot);
        var key = keyFor(handler.keyAt(slot), stack);
        return key == null ? stack : rest(stack, count, handler.insert(slot, key, count, simulate));
    }

    @Override
    @SuppressWarnings("unchecked")
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        var inv = inv0;
        int l = slot;
        int d;
        NotifiableInventory<?> o;
        if (inv != null) {
            d = direct0;
            o = owner0;
        } else if ((inv = invs[slot]) != null) {
            l = locals[slot];
            d = direct[slot];
            o = owners[slot];
        } else {
            var lo = local;
            if (lo != null) lo.touch(slot);
            var key = handler.keyAt(slot);
            if (key == null) return ItemStack.EMPTY;
            long n = handler.extract(slot, key, amount, simulate);
            return n <= 0 ? ItemStack.EMPTY : key.toStack((int) n);
        }
        if (inv.amountAt(l) == 0) return ItemStack.EMPTY;
        var st = peek(slot, inv);
        if (st != null && st.violated(l)) {
            st.settle(l);
            if (inv.amountAt(l) == 0) return ItemStack.EMPTY;
        }
        var key = (AEItemKey) inv.rawKeyAt(l);
        long n;
        if ((d & EXTRACT) == 0) {
            n = handler.extract(slot, key, amount, simulate);
        } else {
            if (o != null && !o.canCapOutput()) return ItemStack.EMPTY;
            n = ((KeyInventory<AEItemKey>) inv).extract(l, key, amount, simulate);
        }
        if (n <= 0) return ItemStack.EMPTY;
        return simulate || st == null ? key.toStack((int) n) : st.taken(inv, l, key, n);
    }

    @Override
    public int getSlotLimit(int slot) {
        int u = uniformLimit;
        if (u >= 0) return u;
        var inv = inv0;
        if (inv != null) return Keys.saturatedInt((direct0 & LIMIT) != 0 ? inv.slotLimit() : owner0.slotLimit(slot));
        inv = invs[slot];
        if (inv != null) return Keys.saturatedInt((direct[slot] & LIMIT) != 0 ? inv.slotLimit() : owners[slot].slotLimit(locals[slot]));
        return Keys.saturatedInt(handler.slotLimit(slot));
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        var key = AEItemKey.of(stack);
        if (key == null) return false;
        long space;
        var inv = invs[slot];
        if (inv != null && (direct[slot] & INSERT) != 0) {
            var o = owners[slot];
            int l = locals[slot];
            space = o == null ? ((KeyInventory<AEItemKey>) inv).spaceFor(l, key) : ((NotifiableInventory<AEItemKey>) o).insert(l, key, Long.MAX_VALUE, true);
        } else {
            space = handler.spaceFor(slot, key);
        }
        return space > 0 || keyAt(slot) == key;
    }

    @Override
    public @Nullable AEKey getKeyInSlot(int slot) {
        touchSlot(slot);
        return keyAt(slot);
    }

    @Override
    public long getAmountInSlot(int slot) {
        touchSlot(slot);
        return amountAt(slot);
    }
}
