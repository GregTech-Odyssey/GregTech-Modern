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
        if (from.enumerateByKey()) {
            var type = from.keyType();
            for (var stack : from.getAvailableStacks()) {
                var key = stack.getKey();
                long amount = stack.getLongValue();
                if (amount <= 0 || key.getType() != type || (filter != null && !filter.matches(key))) continue;
                @SuppressWarnings("unchecked")
                K k = (K) key;
                long accept = to.insert(k, Math.min(left, amount), true);
                if (accept <= 0) continue;
                left -= transferKey(from, to, k, accept);
                if (left < 1) break;
            }
        } else {
            int size = from.size();
            for (int i = 0; i < size && left > 0; i++) {
                left -= transferSlot(from, i, to, left, filter);
            }
        }
        return maxAmount - left;
    }

    public static <K extends AEKey> long transferSlot(IKeyHandler<K> from, int slot, IKeyHandler<K> to, long maxAmount, @Nullable AEKeyFilter filter) {
        if (maxAmount <= 0) return 0;
        long stored = from.amountAt(slot);
        if (stored <= 0) return 0;
        K key = from.keyAt(slot);
        if (filter != null && !filter.matches(key)) return 0;
        long accept = to.insert(key, Math.min(stored, maxAmount), true);
        if (accept <= 0) return 0;
        long extracted = from.extract(slot, key, accept, false);
        if (extracted <= 0) return 0;
        long inserted = to.insert(key, extracted, false);
        if (inserted < extracted) {
            var origin = from.unrestricted();
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
        if (extracted <= 0) return 0;
        long inserted = to.insert(key, extracted, false);
        if (inserted < extracted) from.unrestricted().insert(key, extracted - inserted, false);
        return inserted;
    }
}
