package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;

public class TieredMachine extends MetaMachine implements ITieredMachine {

    protected final int tier;

    public TieredMachine(IMachineBlockEntity holder, int tier) {
        super(holder);
        this.tier = tier;
    }

    public int getTier() {
        return this.tier;
    }
}
