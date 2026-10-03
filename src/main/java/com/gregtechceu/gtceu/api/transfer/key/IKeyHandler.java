package com.gregtechceu.gtceu.api.transfer.key;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import org.jetbrains.annotations.Nullable;

public interface IKeyHandler<K extends AEKey> {

    AEKeyType keyType();

    int size();

    default boolean fixedSize() {
        return false;
    }

    @Nullable
    K keyAt(int slot);

    long amountAt(int slot);

    long slotLimit(int slot);

    long insert(int slot, K key, long amount, boolean simulate);

    long extract(int slot, K key, long amount, boolean simulate);

    default long insert(K key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        long left = amount;
        int size = size();
        for (int i = 0; i < size && left > 0; i++) {
            if (amountAt(i) > 0 && keyAt(i) == key) left -= insert(i, key, left, simulate);
        }
        for (int i = 0; i < size && left > 0; i++) {
            if (amountAt(i) == 0) left -= insert(i, key, left, simulate);
        }
        return amount - left;
    }

    default long extract(K key, long amount, boolean simulate) {
        if (amount <= 0) return 0;
        long left = amount;
        int size = size();
        for (int i = 0; i < size && left > 0; i++) {
            if (amountAt(i) > 0 && keyAt(i) == key) left -= extract(i, key, left, simulate);
        }
        return amount - left;
    }

    default IKeyHandler<K> unrestricted() {
        return this;
    }

    default long spaceFor(int slot, K key) {
        return insert(slot, key, Long.MAX_VALUE, true);
    }

    default long count(K key) {
        long total = 0;
        int size = size();
        for (int i = 0; i < size; i++) {
            if (keyAt(i) == key) {
                long a = amountAt(i);
                total = total + a < 0 ? Long.MAX_VALUE : total + a;
            }
        }
        return total;
    }

    default boolean isEmpty() {
        int size = size();
        for (int i = 0; i < size; i++) {
            if (amountAt(i) > 0) return false;
        }
        return true;
    }
}
