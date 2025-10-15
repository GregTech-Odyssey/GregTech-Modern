package com.gregtechceu.gtceu.utils.memoization;

import java.util.function.Supplier;

public class MemoizedSupplier<T> implements Supplier<T> {

    protected T value = null;
    protected Supplier<T> delegate;

    protected MemoizedSupplier(Supplier<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T get() {
        if (delegate != null) {
            value = delegate.get();
            delegate = null;
        }
        return value;
    }
}
