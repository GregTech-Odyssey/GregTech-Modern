package com.gregtechceu.gtceu.utils.memoization;

import java.util.Map;
import java.util.function.Function;

public class MemoizedFunction<T, R> implements Function<T, R> {

    protected final Function<T, R> delegate;
    protected final Map<T, R> map;

    public MemoizedFunction(Function<T, R> delegate, Map<T, R> map) {
        this.delegate = delegate;
        this.map = map;
    }

    @Override
    public R apply(T t) {
        return map.computeIfAbsent(t, delegate);
    }
}
