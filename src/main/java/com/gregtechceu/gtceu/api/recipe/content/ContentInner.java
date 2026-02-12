package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.capability.INbtConvertible;

public abstract class ContentInner implements INbtConvertible {

    public long amount;

    protected int hashCode;

    public abstract boolean isEmpty();

    public abstract ContentInner copy();

    public abstract ContentInner copy(long amount);
}
