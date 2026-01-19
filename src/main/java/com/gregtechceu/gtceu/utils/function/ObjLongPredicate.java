package com.gregtechceu.gtceu.utils.function;

@FunctionalInterface
public interface ObjLongPredicate<T> {

    boolean test(T o, long l);
}
