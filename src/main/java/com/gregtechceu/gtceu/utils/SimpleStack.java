package com.gregtechceu.gtceu.utils;

public class SimpleStack<T> {

    public final T value;
    public long amount;

    public SimpleStack(T value, long amount) {
        this.value = value;
        this.amount = amount;
    }

    public int getAmount() {
        if (amount > 2147483647L) {
            return Integer.MAX_VALUE;
        } else {
            return (int) amount;
        }
    }

    public void changeAmount(long amount) {
        this.amount -= amount;
    }

    public void shrink(long amount) {
        this.amount -= amount;
    }
}
