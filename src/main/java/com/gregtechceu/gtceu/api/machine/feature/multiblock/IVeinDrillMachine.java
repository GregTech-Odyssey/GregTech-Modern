package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;

public interface IVeinDrillMachine extends IRecipeLogicMachine {

    int getDrillTier();

    int getEnergyTier();
}
