package com.gregtechceu.gtceu.api.transfer.key;

import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyLongMap;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * 把 MEStorage 按单一 key 类型呈现为 GT 存储：存取直接转给 MEStorage；给出存储内容表时以一个现存同类 key 作槽 0（仍在就不换）、
 * 有余量时多给一个空槽，并据内容表列举与计数；可只针对一个标记 key。
 */
public final class MEStorageKeyView<K extends AEKey> implements IKeyHandler<K> {

    private final MEStorage storage;
    private final AEKeyType type;
    @Nullable
    private final AEKeyLongMap<AEKey> contents;
    @Nullable
    private final BooleanSupplier hasRoom;
    @Nullable
    private final K mark;
    @Nullable
    private K cursor;

    public MEStorageKeyView(MEStorage storage, AEKeyType type) {
        this(storage, type, null, null, null);
    }

    public MEStorageKeyView(MEStorage storage, AEKeyType type, AEKeyLongMap<AEKey> contents, BooleanSupplier hasRoom) {
        this(storage, type, contents, hasRoom, null);
    }

    private MEStorageKeyView(MEStorage storage, AEKeyType type, @Nullable AEKeyLongMap<AEKey> contents, @Nullable BooleanSupplier hasRoom, @Nullable K mark) {
        this.storage = storage;
        this.type = type;
        this.contents = contents;
        this.hasRoom = hasRoom;
        this.mark = mark;
    }

    public MEStorageKeyView<K> marked(@Nullable K mark) {
        return mark == this.mark ? this : new MEStorageKeyView<>(storage, type, contents, hasRoom, mark);
    }

    public MEStorage getStorage() {
        return storage;
    }

    public @Nullable K getMark() {
        return mark;
    }

    @Override
    public AEKeyType keyType() {
        return type;
    }

    @Override
    public int size() {
        var room = hasRoom;
        return room == null ? 0 : room.getAsBoolean() ? 2 : 1;
    }

    @SuppressWarnings("unchecked")
    private @Nullable K first() {
        var map = contents;
        if (map == null) return null;
        var m = mark;
        if (m != null) return map.getAmount(m) > 0 ? m : null;
        var c = cursor;
        if (c != null && map.getAmount(c) > 0) return c;
        K found = null;
        for (var e : map) {
            if (e.getLongValue() > 0 && e.getKey().getType() == type) {
                found = (K) e.getKey();
                break;
            }
        }
        if (cursor != found) cursor = found;
        return found;
    }

    @Override
    public @Nullable K keyAt(int slot) {
        return slot == 0 ? first() : null;
    }

    @Override
    public long amountAt(int slot) {
        if (slot != 0) return 0;
        var key = first();
        return key == null ? 0 : contents.getAmount(key);
    }

    @Override
    public long slotLimit(int slot) {
        return Long.MAX_VALUE;
    }

    @Override
    public long insert(int slot, K key, long amount, boolean simulate) {
        return insert(key, amount, simulate);
    }

    @Override
    public long extract(int slot, K key, long amount, boolean simulate) {
        return slot == 0 ? extract(key, amount, simulate) : 0;
    }

    @Override
    public long insert(K key, long amount, boolean simulate) {
        var m = mark;
        return m != null && m != key ? 0 : storage.insert(key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.empty());
    }

    @Override
    public long extract(K key, long amount, boolean simulate) {
        var m = mark;
        return m != null && m != key ? 0 : storage.extract(key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.empty());
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || what.getType() != type) return 0;
        var m = mark;
        return m != null && m != what ? 0 : storage.insert(what, amount, mode, source);
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || what.getType() != type) return 0;
        var m = mark;
        return m != null && m != what ? 0 : storage.extract(what, amount, mode, source);
    }

    @Override
    public long count(K key) {
        var map = contents;
        if (map == null) return 0;
        var m = mark;
        return m != null && m != key ? 0 : map.getAmount(key);
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        var map = contents;
        if (map == null) return;
        var m = mark;
        if (m != null) {
            long amount = map.getAmount(m);
            if (amount > 0) out.add(m, amount);
            return;
        }
        for (var e : map) {
            var key = e.getKey();
            long amount = e.getLongValue();
            if (amount > 0 && key.getType() == type) out.add(key, amount);
        }
    }

    @Override
    public void getAvailableStacks(KeyCounter out, boolean extractableOnly) {
        getAvailableStacks(out);
    }

    @Override
    public boolean containsAny(Set<AEKey> primaryKeys) {
        var map = contents;
        if (map == null) return false;
        var m = mark;
        if (m != null) return map.getAmount(m) > 0 && primaryKeys.contains(m.dropSecondary());
        for (var e : map) {
            var key = e.getKey();
            if (e.getLongValue() > 0 && key.getType() == type && primaryKeys.contains(key.dropSecondary())) return true;
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        return first() == null;
    }

    @Override
    public Component getDescription() {
        return storage.getDescription();
    }
}
