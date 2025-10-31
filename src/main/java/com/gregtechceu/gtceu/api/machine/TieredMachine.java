package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;

import lombok.Getter;

@Getter
public class TieredMachine extends MetaMachine implements ITieredMachine {

    protected final int tier;

    public TieredMachine(MetaMachineBlockEntity holder, int tier) {
        super(holder);
        this.tier = tier;
    }
}
