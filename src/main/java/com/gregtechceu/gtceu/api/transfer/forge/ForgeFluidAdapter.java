package com.gregtechceu.gtceu.api.transfer.forge;

import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.me.storage.ExternalStorageFacade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * GT 流体存储对 Forge 的唯一出口：getFluidInTank 返回与存储共享的视图（存储写该罐即作废），读取不核对；
 * 视图被外部改写时在该罐下次同 key 填充/抽取、重建或 AE 按槽访问时核对（减少按抽取写回，其余恢复并告警）。
 */
public final class ForgeFluidAdapter implements IFluidHandler, ExternalStorageFacade.DirectKeyHandler {

    private static final FluidStack[] NO_VIEWS = new FluidStack[0];
    private static final FluidStack[][] NO_CACHES = new FluidStack[0][];
    private static final FluidViews[] NO_STORES = new FluidViews[0];

    private final IKeyHandler<AEFluidKey> handler;
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
    private FluidStack[] views = NO_VIEWS;
    @Nullable
    private FluidViews store0;
    @Nullable
    private FluidViews local;
    private final AEFluidKey[] recentKeys = new AEFluidKey[4];
    private int recentNext;

    public ForgeFluidAdapter(IKeyHandler<AEFluidKey> handler) {
        this.handler = handler;
        var f = ForgeItemAdapter.Flat.of(handler);
        if (f != null) {
            int n = f.size;
            this.size = n;
            this.inv0 = f.inv0;
            var owner = f.inv0 != null ? f.owners[0] : null;
            boolean direct = f.inv0 != null && (f.direct[0] & ForgeItemAdapter.INSERT) != 0 && (owner == null || ForgeItemAdapter.Flat.plainKeyInsert(owner));
            this.fillInv = direct ? f.inv0 : null;
            this.fillOwner = direct ? owner : null;
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
        } else {
            this.size = -1;
            this.inv0 = null;
            this.fillInv = null;
            this.fillOwner = null;
            this.drainInv = null;
            this.drainOwner = null;
            this.invs = ForgeItemAdapter.Flat.NO_INVS;
            this.locals = ForgeItemAdapter.Flat.NO_LOCALS;
            this.distinct = ForgeItemAdapter.Flat.NO_INVS;
            this.caches = NO_CACHES;
            this.stores = NO_STORES;
        }
    }

    public IKeyHandler<AEFluidKey> getHandler() {
        return handler;
    }

    @Override
    public int getTanks() {
        int n = size;
        return n >= 0 ? n : handler.size();
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        var vs = views;
        if (tank >= 0 && tank < vs.length) {
            var v = vs[tank];
            if (v != null) return v;
        } else if (tank >= 0 && tank < caches.length) {
            var c = caches[tank];
            if (c != null) {
                var v = c[locals[tank]];
                if (v != null) return v;
            }
        }
        return refresh(tank);
    }

    private FluidStack refresh(int tank) {
        if (tank >= 0 && tank < size) {
            var inv = inv0;
            int l = tank;
            if (inv == null) {
                inv = invs[tank];
                l = locals[tank];
            }
            if (inv != null) {
                var st = bind(tank, inv);
                st.touch(l);
                long stored = inv.amountAt(l);
                var v = st.view(l, stored == 0 ? null : (AEFluidKey) inv.rawKeyAt(l), stored);
                st.cache[l] = v;
                return v;
            }
            var lo = local(size);
            lo.touch(tank);
            return lo.view(tank, handler.keyAt(tank), handler.amountAt(tank));
        }
        if (tank < 0 || size >= 0) {
            var key = handler.keyAt(tank);
            return key == null ? FluidStack.EMPTY : key.toStack(Keys.saturatedInt(handler.amountAt(tank)));
        }
        var key = handler.keyAt(tank);
        var lo = local;
        if (key == null && (lo == null || tank >= lo.lent.length)) return FluidStack.EMPTY;
        lo = local(Math.max(tank + 1, handler.size()));
        lo.touch(tank);
        return lo.view(tank, handler.keyAt(tank), handler.amountAt(tank));
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

    private FluidViews local(int n) {
        var lo = local;
        if (lo == null) local = lo = FluidViews.local(handler.unrestricted(), n);
        else if (n > lo.lent.length) local = lo = lo.grown(n);
        return lo;
    }

    private void touch(int tank) {
        if (tank >= 0 && tank < size) {
            var inv = inv0;
            int l = tank;
            if (inv == null) {
                inv = invs[tank];
                l = locals[tank];
            }
            if (inv != null) {
                var st = peek(tank, inv);
                if (st != null) st.touch(l);
                return;
            }
        }
        var lo = local;
        if (lo != null && tank >= 0 && tank < lo.lent.length) lo.touch(tank);
    }

    private void touchKey(AEFluidKey key) {
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

    private void followKey(AEFluidKey key) {
        var inv = inv0;
        if (inv != null) {
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

    private @Nullable AEFluidKey keyAt(int tank) {
        if (tank >= 0 && tank < invs.length) {
            var inv = inv0;
            int l = tank;
            if (inv == null) {
                inv = invs[tank];
                if (inv == null) return handler.keyAt(tank);
                l = locals[tank];
            }
            return inv.amountAt(l) == 0 ? null : (AEFluidKey) inv.rawKeyAt(l);
        }
        return handler.keyAt(tank);
    }

    private long amountAt(int tank) {
        if (tank >= 0 && tank < invs.length) {
            var inv = inv0;
            if (inv != null) return inv.amountAt(tank);
            inv = invs[tank];
            if (inv != null) return inv.amountAt(locals[tank]);
        }
        return handler.amountAt(tank);
    }

    private @Nullable AEFluidKey keyOf(FluidStack stack) {
        if (stack.getTag() != null) return Keys.fluid(stack);
        var fluid = stack.getRawFluid();
        var rs = recentKeys;
        for (var r : rs) {
            if (r != null && r.getFluid() == fluid) return r;
        }
        var k = Keys.fluid(stack);
        if (k != null) rs[recentNext++ & 3] = k;
        return k;
    }

    @Override
    public int getTankCapacity(int tank) {
        return Keys.saturatedInt(handler.slotLimit(tank));
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        var key = Keys.fluidType(stack);
        return key != null && (handler.spaceFor(tank, key) > 0 || keyAt(tank) == key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        var key = keyOf(resource);
        if (key == null) return 0;
        touchKey(key);
        int amount = resource.getAmount();
        boolean simulate = action.simulate();
        var inv = fillInv;
        if (inv != null) {
            var o = fillOwner;
            if (o == null || !o.isVoiding && o.canCapInput()) return (int) ((KeyInventory<AEFluidKey>) inv).insert(key, amount, simulate);
            if (!o.canCapInput()) return 0;
        }
        return (int) handler.insert(key, amount, simulate);
    }

    @Override
    public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        var key = keyOf(resource);
        if (key == null) return FluidStack.EMPTY;
        touchKey(key);
        boolean simulate = action.simulate();
        long n = drainKey(key, resource.getAmount(), simulate);
        if (n <= 0) return FluidStack.EMPTY;
        if (!simulate) followKey(key);
        return key.toStack((int) n);
    }

    @SuppressWarnings("unchecked")
    private long drainKey(AEFluidKey key, long amount, boolean simulate) {
        var inv = drainInv;
        if (inv == null) return handler.extract(key, amount, simulate);
        var o = drainOwner;
        return o == null || o.canCapOutput() ? ((KeyInventory<AEFluidKey>) inv).extract(key, amount, simulate) : 0;
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        int n = getTanks();
        for (int i = 0; i < n; i++) {
            var key = keyAt(i);
            if (key == null) continue;
            touchKey(key);
            long got = drainKey(key, maxDrain, action.simulate());
            if (got > 0) {
                if (action.execute()) followKey(key);
                return key.toStack((int) got);
            }
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int getKeySlots() {
        return getTanks();
    }

    @Override
    public @Nullable AEKey getKeyInSlot(int slot) {
        touch(slot);
        return keyAt(slot);
    }

    @Override
    public long getAmountInSlot(int slot) {
        touch(slot);
        return amountAt(slot);
    }

    @Override
    public long insertKey(AEKey what, long amount, Actionable mode) {
        if (!(what instanceof AEFluidKey key)) return 0;
        return handler.insert(key, amount, mode == Actionable.SIMULATE);
    }

    @Override
    public long extractKey(AEKey what, long amount, Actionable mode) {
        if (!(what instanceof AEFluidKey key)) return 0;
        touchKey(key);
        return handler.extract(key, amount, mode == Actionable.SIMULATE);
    }
}
