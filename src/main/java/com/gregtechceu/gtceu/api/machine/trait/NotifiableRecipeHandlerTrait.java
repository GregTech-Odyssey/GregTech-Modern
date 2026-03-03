package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import lombok.Getter;
import lombok.Setter;

public abstract class NotifiableRecipeHandlerTrait<T> extends NotifiableMachineTrait implements IRecipeHandler<T> {

    @Getter
    @Setter
    @Persisted
    protected boolean isDistinct;

    public NotifiableRecipeHandlerTrait(MetaMachine machine) {
        super(machine);
    }
}
