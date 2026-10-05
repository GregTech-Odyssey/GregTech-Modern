package com.gregtechceu.gtceu.api.transfer.key;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class KeyHandlerView<K extends AEKey> implements IKeyHandler<K> {

    protected final IKeyHandler<K> delegate;
    private final AEKeyType type;
    private final boolean allowInsert;
    private final boolean allowExtract;

    public KeyHandlerView(IKeyHandler<K> delegate) {
        this(delegate, true, true);
    }

    public KeyHandlerView(IKeyHandler<K> delegate, boolean allowInsert, boolean allowExtract) {
        this.delegate = delegate;
        this.type = delegate.keyType();
        this.allowInsert = allowInsert;
        this.allowExtract = allowExtract;
    }

    public static <K extends AEKey> IKeyHandler<K> of(IKeyHandler<K> delegate, boolean allowInsert, boolean allowExtract) {
        if (allowInsert && allowExtract) return delegate;
        return new KeyHandlerView<>(delegate, allowInsert, allowExtract);
    }

    public final IKeyHandler<K> getDelegate() {
        return delegate;
    }

    public final boolean allowsInsert() {
        return allowInsert;
    }

    public final boolean allowsExtract() {
        return allowExtract;
    }

    @Override
    public IKeyHandler<K> unrestricted() {
        return delegate.unrestricted();
    }

    protected boolean canInsert(K key) {
        return allowInsert;
    }

    protected boolean canExtract(K key) {
        return allowExtract;
    }

    protected long limitInsert(int slot, K key, long amount) {
        return amount;
    }

    protected long limitExtract(int slot, K key, long amount) {
        return amount;
    }

    @Override
    public final AEKeyType keyType() {
        return type;
    }

    @Override
    public final boolean supportsKeyType(AEKeyType type) {
        return type == this.type;
    }

    @Override
    public final @Nullable MEStorage forKeyType(AEKeyType type) {
        return type == this.type ? this : null;
    }

    @Override
    public final int size() {
        return delegate.size();
    }

    @Override
    public final boolean fixedSize() {
        return delegate.fixedSize();
    }

    @Override
    public final @Nullable K keyAt(int slot) {
        return delegate.keyAt(slot);
    }

    @Override
    public final long amountAt(int slot) {
        return delegate.amountAt(slot);
    }

    @Override
    public final <R> R readSlot(int slot, int index, SlotReader<? super K, R> reader) {
        return delegate.readSlot(slot, index, reader);
    }

    @Override
    public final long slotLimit(int slot) {
        return delegate.slotLimit(slot);
    }

    @Override
    public final long insert(int slot, K key, long amount, boolean simulate) {
        if (!canInsert(key)) return 0;
        long n = limitInsert(slot, key, amount);
        return n > 0 ? delegate.insert(slot, key, n, simulate) : 0;
    }

    @Override
    public final long extract(int slot, K key, long amount, boolean simulate) {
        if (!canExtract(key)) return 0;
        long n = limitExtract(slot, key, amount);
        return n > 0 ? delegate.extract(slot, key, n, simulate) : 0;
    }

    @Override
    public final long insert(K key, long amount, boolean simulate) {
        if (!canInsert(key)) return 0;
        long n = limitInsert(-1, key, amount);
        return n > 0 ? delegate.insert(key, n, simulate) : 0;
    }

    @Override
    public final long extract(K key, long amount, boolean simulate) {
        if (!canExtract(key)) return 0;
        long n = limitExtract(-1, key, amount);
        return n > 0 ? delegate.extract(key, n, simulate) : 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return amount > 0 && what.getType() == type ? insert((K) what, amount, mode == Actionable.SIMULATE) : 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public final long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return amount > 0 && what.getType() == type ? extract((K) what, amount, mode == Actionable.SIMULATE) : 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        delegate.getAvailableStacks(out);
    }

    @Override
    public void getAvailableStacks(KeyCounter out, boolean extractableOnly) {
        if (extractableOnly && !allowExtract) return;
        IKeyHandler.super.getAvailableStacks(out, extractableOnly);
    }

    @Override
    public boolean containsAny(Set<AEKey> primaryKeys) {
        return delegate.containsAny(primaryKeys);
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }
}
