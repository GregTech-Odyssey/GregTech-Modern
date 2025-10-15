package com.gregtechceu.gtceu.utils.memoization;

import java.util.function.Supplier;

public class CleanableMemoizedSupplier<T> extends MemoizedSupplier<T> {

    protected boolean initialized = false;

    protected CleanableMemoizedSupplier(Supplier<T> delegate) {
        super(delegate);
    }

    @Override
    public T get() {
        if (!initialized) {
            value = delegate.get();
            initialized = true;
        }
        return value;
    }

    public void invalidate() {
        initialized = false;
        value = null;
    }
}
