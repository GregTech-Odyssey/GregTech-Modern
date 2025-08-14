package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;

public class RockCrusherMachine extends SimpleTieredMachine {

    public RockCrusherMachine(MetaMachineBlockEntity holder, int tier, Object... args) {
        super(holder, tier, GTMachineUtils.defaultTankSizeFunction, args);
    }

    @Override
    public boolean shouldWeatherOrTerrainExplosion() {
        return false;
    }
}
