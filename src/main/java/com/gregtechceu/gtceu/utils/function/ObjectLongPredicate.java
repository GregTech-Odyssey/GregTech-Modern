package com.gregtechceu.gtceu.utils.function;

@FunctionalInterface
public interface ObjectLongPredicate<T> {

    boolean test(T o, long l);
}
