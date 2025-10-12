package com.gregtechceu.gtceu.api.machine.steam;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class SteamMachine extends MetaMachine implements ITieredMachine {

    public final boolean isHighPressure;
    @Persisted
    public final NotifiableFluidTank steamTank;

    public SteamMachine(MetaMachineBlockEntity holder, boolean isHighPressure, Object... args) {
        super(holder);
        this.isHighPressure = isHighPressure;
        this.steamTank = createSteamTank(args);
        this.steamTank.setFilter(fluidStack -> fluidStack.getFluid() == GTMaterials.Steam.getFluid());
    }

    @Override
    public int getTier() {
        return isHighPressure ? 1 : 0;
    }

    protected abstract NotifiableFluidTank createSteamTank(Object... args);

    public boolean isHighPressure() {
        return this.isHighPressure;
    }
}
