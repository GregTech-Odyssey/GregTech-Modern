package com.gregtechceu.gtceu.api.transfer.forge;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * 一个流体存储借给 Forge 的视图：cache 与存储共享，存储写某罐即置空该罐；lent 记录借出视图与借出时的量/标签，
 * 被外部改写的借出视图在该罐下一次同 key 填充/抽取或 AE 按槽访问时结算（减少按差额从 src 抽走，其余恢复并告警）。
 */
final class FluidViews implements IKeyHandler.SlotReader<AEFluidKey, FluidStack> {

    private static long lastWarn;

    final IKeyHandler<AEFluidKey> src;
    final FluidStack[] cache;
    final FluidStack[] lent;
    final AEFluidKey[] keys;
    final int[] amounts;
    final CompoundTag[] tags;
    long lentBits;
    boolean viewed;

    private FluidViews(IKeyHandler<AEFluidKey> src, int size) {
        this.src = src;
        this.cache = new FluidStack[size];
        this.lent = new FluidStack[size];
        this.keys = new AEFluidKey[size];
        this.amounts = new int[size];
        this.tags = new CompoundTag[size];
    }

    private FluidViews(FluidViews from, int size) {
        this.src = from.src;
        this.cache = Arrays.copyOf(from.cache, size);
        this.lent = Arrays.copyOf(from.lent, size);
        this.keys = Arrays.copyOf(from.keys, size);
        this.amounts = Arrays.copyOf(from.amounts, size);
        this.tags = Arrays.copyOf(from.tags, size);
        this.lentBits = from.lentBits;
        this.viewed = from.viewed;
    }

    static @Nullable FluidViews peek(KeyInventory<?> inv) {
        return inv.viewOwner() instanceof FluidViews v ? v : null;
    }

    @SuppressWarnings("unchecked")
    static FluidViews attach(KeyInventory<?> inv) {
        if (inv.viewOwner() instanceof FluidViews v) return v;
        var v = new FluidViews((IKeyHandler<AEFluidKey>) inv, inv.size());
        inv.attachViews(v, v.cache);
        return v;
    }

    static FluidViews local(IKeyHandler<AEFluidKey> src, int size) {
        return new FluidViews(src, size);
    }

    FluidViews grown(int size) {
        return size <= lent.length ? this : new FluidViews(this, size);
    }

    boolean lent(AEKey key) {
        return (lentBits & 1L << key.getUid()) != 0;
    }

    boolean violated(int t) {
        var v = lent[t];
        return v != null && (v.getAmount() != amounts[t] || v.getTag() != tags[t]);
    }

    void touch(int t) {
        if (violated(t)) settle(t);
    }

    void settle(int t) {
        var view = lent[t];
        int expect = amounts[t];
        int now = view.getAmount();
        var key = keys[t];
        cache[t] = null;
        if (view.getTag() == tags[t] && view.getRawFluid() == key.getFluid() && now < expect) {
            long taken = expect - Math.max(now, 0);
            if (now > 0) amounts[t] = now;
            else drop(t);
            long done = src.extract(t, key, taken, false);
            if (done < taken) warn("drained more than the view allowed", view);
            return;
        }
        drop(t);
        warn("modified a read view", view);
    }

    void touchKey(AEKey key) {
        if (lent(key)) settleKey(key);
    }

    private void settleKey(AEKey key) {
        var ks = keys;
        long bits = 0;
        for (int t = 0; t < ks.length; t++) {
            var k = ks[t];
            if (k == null) continue;
            if (k == key && violated(t)) {
                settle(t);
                k = ks[t];
                if (k == null) continue;
            }
            bits |= 1L << k.getUid();
        }
        lentBits = bits;
    }

    @Override
    public FluidStack read(int index, @Nullable AEFluidKey key, long amount) {
        return view(index, key, amount);
    }

    FluidStack view(int t, @Nullable AEFluidKey key, long stored) {
        int amount = key == null ? 0 : Keys.saturatedInt(stored);
        if (amount <= 0) {
            if (keys[t] != null) drop(t);
            return FluidStack.EMPTY;
        }
        if (keys[t] == key) {
            var v = lent[t];
            if (amounts[t] != amount) {
                v.setAmount(amount);
                amounts[t] = amount;
            }
            return v;
        }
        var v = key.toStack(amount);
        lent[t] = v;
        keys[t] = key;
        tags[t] = v.getTag();
        amounts[t] = amount;
        lentBits |= 1L << key.getUid();
        viewed = true;
        return v;
    }

    void follow(AEFluidKey key) {
        if (lent(key)) refollow(key);
    }

    private void refollow(AEFluidKey key) {
        var ks = keys;
        for (int t = 0; t < ks.length; t++) {
            if (ks[t] != key || violated(t)) continue;
            long stored = src.amountAt(t);
            if (stored > 0 && src.keyAt(t) == key) {
                int amount = Keys.saturatedInt(stored);
                var v = lent[t];
                if (amounts[t] != amount) {
                    v.setAmount(amount);
                    amounts[t] = amount;
                }
                cache[t] = v;
            } else {
                drop(t);
                if (stored == 0) cache[t] = FluidStack.EMPTY;
            }
        }
    }

    private void drop(int t) {
        cache[t] = null;
        lent[t] = null;
        keys[t] = null;
        tags[t] = null;
        amounts[t] = 0;
    }

    private static void warn(String what, FluidStack view) {
        long now = System.currentTimeMillis();
        if (now - lastWarn > 10000) {
            lastWarn = now;
            GTCEu.LOGGER.warn("An external mod {} of a GregTech tank ({})", what, view.getRawFluid());
        }
    }
}
