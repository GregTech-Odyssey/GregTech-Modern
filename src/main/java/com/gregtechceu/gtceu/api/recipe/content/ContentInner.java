package com.gregtechceu.gtceu.api.recipe.content;

import net.minecraft.network.chat.Component;

public abstract class ContentInner {

    public final long amount;

    protected int hashCode;

    protected ContentInner(long amount) {
        this.amount = amount;
    }

    public abstract boolean isEmpty();

    public abstract ContentInner copy(long amount);

    public abstract Component getName();
}
