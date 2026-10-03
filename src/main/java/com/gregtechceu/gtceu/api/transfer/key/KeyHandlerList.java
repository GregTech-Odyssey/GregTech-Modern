package com.gregtechceu.gtceu.api.transfer.key;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class KeyHandlerList<K extends AEKey> implements IKeyHandler<K> {

    private final AEKeyType type;
    private final IKeyHandler<K>[] handlers;
    private final int[] sizes;
    private final int[] offsets;
    private final int total;
    private int lastMember;
    @Nullable
    private IKeyHandler<K> unrestricted;

    @SuppressWarnings("unchecked")
    public KeyHandlerList(AEKeyType type, List<? extends IKeyHandler<K>> handlers) {
        this(type, (IKeyHandler<K>[]) handlers.toArray(new IKeyHandler[0]));
    }

    @SafeVarargs
    public KeyHandlerList(AEKeyType type, IKeyHandler<K>... handlers) {
        this.type = type;
        this.handlers = handlers;
        int n = handlers.length;
        int[] sizes = new int[n];
        int[] offsets = new int[n + 1];
        boolean fixed = true;
        for (int i = 0; i < n; i++) {
            var h = handlers[i];
            if (h.fixedSize()) {
                int s = h.size();
                sizes[i] = s;
                offsets[i + 1] = offsets[i] + s;
            } else {
                sizes[i] = -1;
                fixed = false;
            }
        }
        this.sizes = sizes;
        this.offsets = fixed ? offsets : null;
        this.total = fixed ? offsets[n] : -1;
    }

    public IKeyHandler<K>[] handlers() {
        return handlers;
    }

    @Override
    @SuppressWarnings("unchecked")
    public IKeyHandler<K> unrestricted() {
        var u = unrestricted;
        if (u == null) {
            boolean same = true;
            IKeyHandler<K>[] raw = new IKeyHandler[handlers.length];
            for (int i = 0; i < handlers.length; i++) {
                raw[i] = handlers[i].unrestricted();
                if (raw[i] != handlers[i]) same = false;
            }
            u = same ? this : new KeyHandlerList<>(type, raw);
            unrestricted = u;
        }
        return u;
    }

    @Override
    public AEKeyType keyType() {
        return type;
    }

    @Override
    public boolean fixedSize() {
        return offsets != null;
    }

    @Override
    public int size() {
        if (offsets != null) return total;
        var hs = handlers;
        int[] sz = sizes;
        int n = 0;
        for (int i = 0; i < hs.length; i++) {
            int s = sz[i];
            n += s >= 0 ? s : hs[i].size();
        }
        return n;
    }

    private long locate(int slot) {
        int[] off = offsets;
        if (off != null) {
            if (slot >= total) return -1;
            if (slot < 0) return off.length > 1 ? slot & 0xFFFFFFFFL : -1;
            int m = lastMember;
            if (slot < off[m] || slot >= off[m + 1]) {
                int lo = 0;
                int hi = off.length - 2;
                while (lo < hi) {
                    int mid = (lo + hi + 1) >>> 1;
                    if (off[mid] <= slot) lo = mid;
                    else hi = mid - 1;
                }
                m = lo;
                lastMember = m;
            }
            return (long) m << 32 | (slot - off[m]) & 0xFFFFFFFFL;
        }
        var hs = handlers;
        int[] sz = sizes;
        for (int i = 0; i < hs.length; i++) {
            int s = sz[i];
            if (s < 0) s = hs[i].size();
            if (slot < s) return (long) i << 32 | slot & 0xFFFFFFFFL;
            slot -= s;
        }
        return -1;
    }

    @Override
    public @Nullable K keyAt(int slot) {
        long loc = locate(slot);
        return loc < 0 ? null : handlers[(int) (loc >>> 32)].keyAt((int) loc);
    }

    @Override
    public long amountAt(int slot) {
        long loc = locate(slot);
        return loc < 0 ? 0 : handlers[(int) (loc >>> 32)].amountAt((int) loc);
    }

    @Override
    public long slotLimit(int slot) {
        long loc = locate(slot);
        return loc < 0 ? 0 : handlers[(int) (loc >>> 32)].slotLimit((int) loc);
    }

    @Override
    public long insert(int slot, K key, long amount, boolean simulate) {
        long loc = locate(slot);
        return loc < 0 ? 0 : handlers[(int) (loc >>> 32)].insert((int) loc, key, amount, simulate);
    }

    @Override
    public long extract(int slot, K key, long amount, boolean simulate) {
        long loc = locate(slot);
        return loc < 0 ? 0 : handlers[(int) (loc >>> 32)].extract((int) loc, key, amount, simulate);
    }

    @Override
    public long insert(K key, long amount, boolean simulate) {
        long left = amount;
        for (var h : handlers) {
            if (left <= 0) break;
            left -= h.insert(key, left, simulate);
        }
        return amount - left;
    }

    @Override
    public long extract(K key, long amount, boolean simulate) {
        long left = amount;
        for (var h : handlers) {
            if (left <= 0) break;
            left -= h.extract(key, left, simulate);
        }
        return amount - left;
    }
}
