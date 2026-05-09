package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandler;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

public interface IRecipeHandlerTrait extends IRecipeHandler {

    IO getHandlerIO();

    /**
     * add listener for notification when it changed.
     */
    ISubscription addChangedListener(Runnable listener);
}
