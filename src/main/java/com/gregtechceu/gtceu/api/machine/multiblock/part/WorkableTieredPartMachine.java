package com.gregtechceu.gtceu.api.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;

import lombok.Getter;

@Getter
public class WorkableTieredPartMachine extends WorkableMultiblockPartMachine implements ITieredMachine {

    protected final int tier;

    public WorkableTieredPartMachine(MetaMachineBlockEntity holder, int tier) {
        super(holder);
        this.tier = tier;
    }
}
