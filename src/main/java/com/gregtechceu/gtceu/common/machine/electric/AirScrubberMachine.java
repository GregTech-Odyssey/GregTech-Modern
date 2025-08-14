package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;

public class AirScrubberMachine extends SimpleTieredMachine {

    public AirScrubberMachine(MetaMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier, GTMachineUtils.largeTankSizeFunction, args);
    }
}
