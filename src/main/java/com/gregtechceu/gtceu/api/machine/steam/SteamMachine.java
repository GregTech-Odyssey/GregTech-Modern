package com.gregtechceu.gtceu.api.machine.steam;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.MethodsReturnNonnullByDefault;

import com.gto.datasynclib.annotations.SaveToDisk;
import lombok.Getter;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class SteamMachine extends MetaMachine implements ITieredMachine {

    @Getter
    public final boolean isHighPressure;
    @SaveToDisk
    public final NotifiableFluidTank steamTank;

    public SteamMachine(MetaMachineBlockEntity holder, boolean isHighPressure, Object... args) {
        super(holder);
        this.isHighPressure = isHighPressure;
        this.steamTank = createSteamTank(args);
        this.steamTank.setFilter(fluidStack -> fluidStack.getFluid() == GTMaterials.Steam.getFluid());
    }

    @Override
    public int getRecipeTier() {
        return 1;
    }

    @Override
    public int getTier() {
        return isHighPressure ? 1 : 0;
    }

    protected abstract NotifiableFluidTank createSteamTank(Object... args);
}
