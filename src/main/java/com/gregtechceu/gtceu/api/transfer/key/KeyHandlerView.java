package com.gregtechceu.gtceu.api.transfer.key;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import org.jetbrains.annotations.Nullable;

public class KeyHandlerView<K extends AEKey> implements IKeyHandler<K> {

    protected final IKeyHandler<K> delegate;
    private final boolean allowInsert;
    private final boolean allowExtract;

    public KeyHandlerView(IKeyHandler<K> delegate) {
        this(delegate, true, true);
    }

    public KeyHandlerView(IKeyHandler<K> delegate, boolean allowInsert, boolean allowExtract) {
        this.delegate = delegate;
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
        return delegate.keyType();
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
}
