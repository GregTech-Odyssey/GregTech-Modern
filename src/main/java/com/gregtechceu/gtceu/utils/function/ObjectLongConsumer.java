package com.gregtechceu.gtceu.utils.function;

@FunctionalInterface
public interface ObjectLongConsumer<T> {

    void accept(T o, long l);
}
