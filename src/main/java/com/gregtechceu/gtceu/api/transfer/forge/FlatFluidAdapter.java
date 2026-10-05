package com.gregtechceu.gtceu.api.transfer.forge;

import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FlatFluidAdapter extends ForgeFluidAdapter {

    private static final FluidStack[][] NO_CACHES = new FluidStack[0][];
    private static final FluidViews[] NO_STORES = new FluidViews[0];

    private final int size;
    @Nullable
    private final KeyInventory<?> inv0;
    @Nullable
    private final KeyInventory<?> fillInv;
    @Nullable
    private final NotifiableInventory<?> fillOwner;
    @Nullable
    private final KeyInventory<?> drainInv;
    @Nullable
    private final NotifiableInventory<?> drainOwner;
    private final KeyInventory<?>[] invs;
    private final int[] locals;
    private final KeyInventory<?>[] distinct;
    private final FluidStack[][] caches;
    private final FluidViews[] stores;
    private final int uniformCapacity;
    @Nullable
    private FluidStack[] views;
    @Nullable
    private FluidViews store0;

    FlatFluidAdapter(IKeyHandler<AEFluidKey> handler, ForgeItemAdapter.Flat f) {
        super(handler);
        int n = f.size;
        this.size = n;
        this.inv0 = f.inv0;
        var owner = f.inv0 != null ? f.owners[0] : null;
        boolean fill = f.inv0 != null && (f.direct[0] & ForgeItemAdapter.INSERT) != 0 && (owner == null || ForgeItemAdapter.Flat.plainKeyInsert(owner));
        this.fillInv = fill ? f.inv0 : null;
        this.fillOwner = fill ? owner : null;
        boolean drain = f.inv0 != null && (f.direct[0] & ForgeItemAdapter.EXTRACT) != 0 && (owner == null || ForgeItemAdapter.Flat.plainKeyExtract(owner));
        this.drainInv = drain ? f.inv0 : null;
        this.drainOwner = drain ? owner : null;
        this.invs = f.invs;
        this.locals = f.locals;
        if (f.inv0 != null) {
            this.distinct = ForgeItemAdapter.Flat.NO_INVS;
            this.caches = NO_CACHES;
            this.stores = NO_STORES;
        } else {
            this.distinct = ForgeItemAdapter.Flat.distinct(f.invs);
            this.caches = new FluidStack[n][];
            this.stores = new FluidViews[n];
        }
        this.uniformCapacity = f.uniformLimit();
    }

    @Override
    public int getTanks() {
        return size;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        if (inv0 != null) {
            var vs = views;
            if (vs != null) {
                var v = vs[tank];
                if (v != null) return v;
            }
        } else {
            var c = caches[tank];
            if (c != null) {
                var v = c[locals[tank]];
                if (v != null) return v;
            }
        }
        return refresh(tank);
    }

    private FluidStack refresh(int tank) {
        var inv = inv0;
        int l = tank;
        if (inv == null) {
            inv = invs[tank];
            l = locals[tank];
            if (inv == null) {
                var lo = local(size);
                lo.touch(tank);
                return handler.readSlot(tank, tank, lo);
            }
        }
        var st = bind(tank, inv);
        st.touch(l);
        long stored = inv.amountAt(l);
        var v = st.view(l, stored == 0 ? null : (AEFluidKey) inv.rawKeyAt(l), stored);
        st.cache[l] = v;
        return v;
    }

    private FluidViews bind(int tank, KeyInventory<?> inv) {
        var st = inv0 != null ? store0 : stores[tank];
        if (st == null) {
            st = FluidViews.attach(inv);
            bindAll(inv, st);
        }
        return st;
    }

    private @Nullable FluidViews peek(int tank, KeyInventory<?> inv) {
        var st = inv0 != null ? store0 : stores[tank];
        if (st == null && (st = FluidViews.peek(inv)) != null) bindAll(inv, st);
        return st;
    }

    private void bindAll(KeyInventory<?> inv, FluidViews st) {
        if (inv0 != null) {
            store0 = st;
            views = st.cache;
            return;
        }
        var is = invs;
        for (int t = 0; t < is.length; t++) {
            if (is[t] == inv) {
                stores[t] = st;
                caches[t] = st.cache;
            }
        }
    }

    @Override
    void touch(int tank) {
        var inv = inv0;
        int l = tank;
        if (inv == null) {
            inv = invs[tank];
            l = locals[tank];
        }
        if (inv != null) {
            var st = peek(tank, inv);
            if (st != null) st.touch(l);
        } else {
            var lo = local;
            if (lo != null) lo.touch(tank);
        }
    }

    @Override
    void touchKey(AEFluidKey key) {
        var st = store0;
        if (st != null && local == null) st.touchKey(key);
        else touchKeys(key);
    }

    private void touchKeys(AEFluidKey key) {
        var inv = inv0;
        if (inv != null) {
            var st = store0;
            if (st == null && (st = FluidViews.peek(inv)) != null) bindAll(inv, st);
            if (st != null) st.touchKey(key);
        } else {
            for (var d : distinct) {
                var st = FluidViews.peek(d);
                if (st != null) st.touchKey(key);
            }
        }
        var lo = local;
        if (lo != null) lo.touchKey(key);
    }

    @Override
    void followKey(AEFluidKey key) {
        var st = store0;
        if (st != null && local == null) st.follow(key);
        else followKeys(key);
    }

    private void followKeys(AEFluidKey key) {
        if (inv0 != null) {
            var st = store0;
            if (st != null) st.follow(key);
        } else {
            for (var d : distinct) {
                var st = FluidViews.peek(d);
                if (st != null) st.follow(key);
            }
        }
        var lo = local;
        if (lo != null) lo.follow(key);
    }

    @Override
    @Nullable
    AEFluidKey keyAt(int tank) {
        var inv = inv0;
        int l = tank;
        if (inv == null) {
            inv = invs[tank];
            if (inv == null) return handler.keyAt(tank);
            l = locals[tank];
        }
        return inv.amountAt(l) == 0 ? null : (AEFluidKey) inv.rawKeyAt(l);
    }

    @Override
    long amountAt(int tank) {
        var inv = inv0;
        if (inv != null) return inv.amountAt(tank);
        inv = invs[tank];
        return inv != null ? inv.amountAt(locals[tank]) : handler.amountAt(tank);
    }

    @Override
    @SuppressWarnings("unchecked")
    int fillKey(AEFluidKey key, int amount, boolean simulate) {
        var inv = fillInv;
        if (inv != null) {
            var o = fillOwner;
            if (o == null || !o.isVoiding && o.canCapInput()) return (int) ((KeyInventory<AEFluidKey>) inv).insert(key, amount, simulate);
            if (!o.canCapInput()) return 0;
        }
        return (int) handler.insert(key, amount, simulate);
    }

    @Override
    @SuppressWarnings("unchecked")
    long drainKey(AEFluidKey key, long amount, boolean simulate) {
        var inv = drainInv;
        if (inv == null) return handler.extract(key, amount, simulate);
        var o = drainOwner;
        return o == null || o.canCapOutput() ? ((KeyInventory<AEFluidKey>) inv).extract(key, amount, simulate) : 0;
    }

    @Override
    public int getTankCapacity(int tank) {
        int u = uniformCapacity;
        return u >= 0 ? u : Keys.saturatedInt(handler.slotLimit(tank));
    }
}
