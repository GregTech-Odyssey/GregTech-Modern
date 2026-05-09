package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.capability.INbtConvertible;

public abstract class ContentInner implements INbtConvertible {

    public final long amount;

    protected int hashCode;

    protected ContentInner(long amount) {
        this.amount = amount;
    }

    public abstract boolean isEmpty();

    public abstract ContentInner copy(long amount);
}
