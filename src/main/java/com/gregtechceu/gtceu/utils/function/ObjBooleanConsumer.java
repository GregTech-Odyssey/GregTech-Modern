package com.gregtechceu.gtceu.utils.function;

@FunctionalInterface
public interface ObjBooleanConsumer<T> {

    void accept(T t, boolean value);
}
