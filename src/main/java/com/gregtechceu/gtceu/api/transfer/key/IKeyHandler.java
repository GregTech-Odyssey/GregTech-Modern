package com.gregtechceu.gtceu.api.transfer.key;

import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.KeyTypedStorage;
import appeng.api.storage.MEStorage;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * GT 内部 key 存储：调用方保证 amount > 0 且 key 类型等于 {@link #keyType()}；keyAt(i) 为 null 当且仅当 amountAt(i) 为 0。
 * 作为 MEStorage 暴露时只在 MEStorage 方法入口校验一次，内部实现不再重复校验。
 */
public interface IKeyHandler<K extends AEKey> extends KeyTypedStorage {

    @FunctionalInterface
    interface SlotReader<K extends AEKey, R> {

        R read(int index, @Nullable K key, long amount);
    }

    AEKeyType keyType();

    int size();

    default boolean fixedSize() {
        return false;
    }

    @Nullable
    K keyAt(int slot);

    long amountAt(int slot);

    default <R> R readSlot(int slot, int index, SlotReader<? super K, R> reader) {
        long amount = amountAt(slot);
        return reader.read(index, amount == 0 ? null : keyAt(slot), amount);
    }

    long slotLimit(int slot);

    long insert(int slot, K key, long amount, boolean simulate);

    long extract(int slot, K key, long amount, boolean simulate);

    default long insert(K key, long amount, boolean simulate) {
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

    @Override
    default boolean supportsKeyType(AEKeyType type) {
        return type == keyType();
    }

    @Override
    default @Nullable MEStorage forKeyType(AEKeyType type) {
        return type == keyType() ? this : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    default long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return amount > 0 && what.getType() == keyType() ? insert((K) what, amount, mode == Actionable.SIMULATE) : 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    default long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return amount > 0 && what.getType() == keyType() ? extract((K) what, amount, mode == Actionable.SIMULATE) : 0;
    }

    @Override
    default void getAvailableStacks(KeyCounter out) {
        int size = size();
        for (int i = 0; i < size; i++) {
            long a = amountAt(i);
            if (a > 0) out.add(keyAt(i), a);
        }
    }

    @Override
    default void getAvailableStacks(KeyCounter out, boolean extractableOnly) {
        if (!extractableOnly) {
            getAvailableStacks(out);
            return;
        }
        int size = size();
        for (int i = 0; i < size; i++) {
            long a = amountAt(i);
            if (a > 0) {
                K key = keyAt(i);
                long n = extract(i, key, a, true);
                if (n > 0) out.add(key, n);
            }
        }
    }

    @Override
    default boolean containsAny(Set<AEKey> primaryKeys) {
        int size = size();
        for (int i = 0; i < size; i++) {
            if (amountAt(i) > 0 && primaryKeys.contains(keyAt(i).dropSecondary())) return true;
        }
        return false;
    }

    @Override
    default Component getDescription() {
        return keyType().getDescription();
    }

    default boolean isEmpty() {
        int size = size();
        for (int i = 0; i < size; i++) {
            if (amountAt(i) > 0) return false;
        }
        return true;
    }
}
