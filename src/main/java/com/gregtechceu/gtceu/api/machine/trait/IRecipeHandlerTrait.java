package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

public interface IRecipeHandlerTrait<K> extends IRecipeHandler<K> {

    /**
     * add listener for notification when it changed.
     */
    ISubscription addChangedListener(Runnable listener);

    void setDistinct(boolean distinct);
}
