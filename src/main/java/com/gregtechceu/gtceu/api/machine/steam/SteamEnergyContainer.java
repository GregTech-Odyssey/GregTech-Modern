package com.gregtechceu.gtceu.api.machine.steam;

import com.gregtechceu.gtceu.api.machine.feature.IDummyEnergyMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import appeng.api.stacks.AEFluidKey;

public class SteamEnergyContainer extends IDummyEnergyMachine.DummyContainer {

    private static final AEFluidKey STEAM = AEFluidKey.of(GTMaterials.Steam.getFluid());

    public final double conversionRate;
    public final NotifiableInventory<AEFluidKey> steamTank;

    public SteamEnergyContainer(double conversionRate, NotifiableInventory<AEFluidKey> steamTank) {
        super((long) (steamTank.storage.slotLimit(0) / conversionRate));
        this.conversionRate = conversionRate;
        this.steamTank = steamTank;
    }

    @Override
    public long changeEnergy(long differenceAmount) {
        differenceAmount = -differenceAmount;
        long totalSteam = (long) (differenceAmount * conversionRate);
        if (totalSteam > 0) {
            long leftSteam = steamTank.storage.extract(STEAM, totalSteam, false);
            if (leftSteam == totalSteam) return -differenceAmount;
            differenceAmount = (long) (leftSteam / conversionRate);
        }
        return -differenceAmount;
    }

    @Override
    public long getEnergyStored() {
        return (long) (steamTank.storage.amountAt(0) / conversionRate);
    }
}
