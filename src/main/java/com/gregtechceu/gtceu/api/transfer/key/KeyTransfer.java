package com.gregtechceu.gtceu.api.transfer.key;

import appeng.api.stacks.AEKey;
import appeng.api.storage.AEKeyFilter;
import org.jetbrains.annotations.Nullable;

public final class KeyTransfer {

    private KeyTransfer() {}

    public static <K extends AEKey> long transfer(IKeyHandler<K> from, IKeyHandler<K> to, long maxAmount) {
        return transfer(from, to, maxAmount, null);
    }

    public static <K extends AEKey> long transfer(IKeyHandler<K> from, IKeyHandler<K> to, long maxAmount, @Nullable AEKeyFilter filter) {
        long left = maxAmount;
        int size = from.size();
        for (int i = 0; i < size && left > 0; i++) {
            left -= transferSlot(from, i, to, left, filter);
        }
        return maxAmount - left;
    }

    public static <K extends AEKey> long transferSlot(IKeyHandler<K> from, int slot, IKeyHandler<K> to, long maxAmount, @Nullable AEKeyFilter filter) {
        if (maxAmount <= 0) return 0;
        long stored = from.amountAt(slot);
        if (stored <= 0) return 0;
        K key = from.keyAt(slot);
        if (key == null || (filter != null && !filter.matches(key))) return 0;
        long want = from.extract(slot, key, stored < maxAmount ? stored : maxAmount, true);
        if (want <= 0) return 0;
        long accept = to.insert(key, want, true);
        if (accept <= 0) return 0;
        long extracted = from.extract(slot, key, accept, false);
        if (extracted <= 0) return 0;
        long inserted = to.insert(key, extracted, false);
        if (inserted < extracted) {
            var origin = unwrap(from);
            long back = origin.insert(slot, key, extracted - inserted, false);
            if (back < extracted - inserted) back += origin.insert(key, extracted - inserted - back, false);
            if (back < extracted - inserted) inserted += to.insert(key, extracted - inserted - back, false);
        }
        return inserted;
    }

    public static <K extends AEKey> long transferKey(IKeyHandler<K> from, IKeyHandler<K> to, K key, long amount) {
        long want = from.extract(key, amount, true);
        if (want <= 0) return 0;
        long accept = to.insert(key, want, true);
        if (accept <= 0) return 0;
        long extracted = from.extract(key, accept, false);
        long inserted = to.insert(key, extracted, false);
        if (inserted < extracted) unwrap(from).insert(key, extracted - inserted, false);
        return inserted;
    }

    private static <K extends AEKey> IKeyHandler<K> unwrap(IKeyHandler<K> handler) {
        return handler.unrestricted();
    }
}
