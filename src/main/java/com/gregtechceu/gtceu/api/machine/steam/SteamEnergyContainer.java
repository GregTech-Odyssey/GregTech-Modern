package com.gregtechceu.gtceu.api.machine.steam;

import com.gregtechceu.gtceu.api.machine.feature.IDummyEnergyMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.utils.GTMath;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

public class SteamEnergyContainer extends IDummyEnergyMachine.DummyContainer {

    private static final FluidStack STEAM = GTMaterials.Steam.getFluid(1);

    private final double conversionRate;
    private final NotifiableFluidTank steamTank;

    public SteamEnergyContainer(double conversionRate, NotifiableFluidTank steamTank) {
        super(Integer.MAX_VALUE);
        this.conversionRate = conversionRate;
        this.steamTank = steamTank;
    }

    @Override
    public long changeEnergy(long differenceAmount) {
        differenceAmount = -differenceAmount;
        int totalSteam = GTMath.saturatedCast((long) (differenceAmount * conversionRate));
        if (totalSteam > 0) {
            var steam = STEAM.copy();
            steam.setAmount(totalSteam);
            var leftSteam = steamTank.drainInternal(steam, IFluidHandler.FluidAction.EXECUTE).getAmount();
            if (leftSteam == totalSteam) return -differenceAmount;
            differenceAmount = (long) (leftSteam / conversionRate);
        }
        return -differenceAmount;
    }

    @Override
    public long getEnergyStored() {
        return (long) (steamTank.getFluidInTank(0).getAmount() / conversionRate);
    }
}
