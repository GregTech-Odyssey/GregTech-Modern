package com.gregtechceu.gtceu.api.recipe.content;

import net.minecraft.network.chat.Component;

import java.util.function.Predicate;

public abstract class ContentInner<T> implements Predicate<T> {

    public final long amount;

    protected int hashCode;

    protected ContentInner(long amount) {
        this.amount = amount;
    }

    public abstract boolean isEmpty();

    public abstract ContentInner<T> copy(long amount);

    public abstract Component getName();
}
