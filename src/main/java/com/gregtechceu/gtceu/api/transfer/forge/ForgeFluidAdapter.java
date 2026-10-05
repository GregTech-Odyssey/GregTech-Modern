package com.gregtechceu.gtceu.api.transfer.forge;

import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
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
public sealed class ForgeFluidAdapter implements IFluidHandler, ExternalStorageFacade.DirectKeyHandler permits FlatFluidAdapter {

    final IKeyHandler<AEFluidKey> handler;
    @Nullable
    FluidViews local;

    public ForgeFluidAdapter(IKeyHandler<AEFluidKey> handler) {
        this.handler = handler;
    }

    static ForgeFluidAdapter of(IKeyHandler<AEFluidKey> handler) {
        var f = ForgeItemAdapter.Flat.of(handler);
        return f == null ? new ForgeFluidAdapter(handler) : new FlatFluidAdapter(handler, f);
    }

    public final IKeyHandler<AEFluidKey> getHandler() {
        return handler;
    }

    @Override
    public int getTanks() {
        return handler.size();
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        var lo = local;
        if (lo != null && tank >= 0 && tank < lo.lent.length) {
            lo.touch(tank);
            return handler.readSlot(tank, tank, lo);
        }
        return firstView(tank);
    }

    private FluidStack firstView(int tank) {
        if (tank < 0) return FluidStack.EMPTY;
        var key = handler.keyAt(tank);
        if (key == null) return FluidStack.EMPTY;
        return local(Math.max(tank + 1, handler.size())).view(tank, key, handler.amountAt(tank));
    }

    final FluidViews local(int n) {
        var lo = local;
        if (lo == null) local = lo = FluidViews.local(handler.unrestricted(), n);
        else if (n > lo.lent.length) local = lo = lo.grown(n);
        return lo;
    }

    void touch(int tank) {
        var lo = local;
        if (lo != null && tank >= 0 && tank < lo.lent.length) lo.touch(tank);
    }

    void touchKey(AEFluidKey key) {
        var lo = local;
        if (lo != null) lo.touchKey(key);
    }

    void followKey(AEFluidKey key) {
        var lo = local;
        if (lo != null) lo.follow(key);
    }

    @Nullable
    AEFluidKey keyAt(int tank) {
        return handler.keyAt(tank);
    }

    long amountAt(int tank) {
        return handler.amountAt(tank);
    }

    int fillKey(AEFluidKey key, int amount, boolean simulate) {
        return (int) handler.insert(key, amount, simulate);
    }

    long drainKey(AEFluidKey key, long amount, boolean simulate) {
        return handler.extract(key, amount, simulate);
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
    public int fill(FluidStack resource, FluidAction action) {
        var key = Keys.fluid(resource);
        if (key == null) return 0;
        touchKey(key);
        return fillKey(key, resource.getAmount(), action.simulate());
    }

    @Override
    public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
        var key = Keys.fluid(resource);
        if (key == null) return FluidStack.EMPTY;
        touchKey(key);
        boolean simulate = action.simulate();
        long n = drainKey(key, resource.getAmount(), simulate);
        if (n <= 0) return FluidStack.EMPTY;
        if (!simulate) followKey(key);
        return key.toStack((int) n);
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain <= 0) return FluidStack.EMPTY;
        boolean simulate = action.simulate();
        int n = getTanks();
        for (int i = 0; i < n; i++) {
            var key = keyAt(i);
            if (key == null) continue;
            touchKey(key);
            long got = drainKey(key, maxDrain, simulate);
            if (got > 0) {
                if (!simulate) followKey(key);
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
    public final long insertKey(AEKey what, long amount, Actionable mode) {
        return what instanceof AEFluidKey key ? handler.insert(key, amount, mode == Actionable.SIMULATE) : 0;
    }

    @Override
    public final long extractKey(AEKey what, long amount, Actionable mode) {
        if (!(what instanceof AEFluidKey key)) return 0;
        touchKey(key);
        return handler.extract(key, amount, mode == Actionable.SIMULATE);
    }
}
