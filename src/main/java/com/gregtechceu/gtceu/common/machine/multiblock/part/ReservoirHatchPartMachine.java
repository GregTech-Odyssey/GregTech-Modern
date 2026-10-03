package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.AEFluidKey;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class ReservoirHatchPartMachine extends FluidHatchPartMachine {

    public static final int FLUID_AMOUNT = 2_000_000_000;

    public ReservoirHatchPartMachine(MetaMachineBlockEntity holder, Object... args) {
        super(holder, GTValues.EV, IO.IN, FLUID_AMOUNT, 1, args);
    }

    //////////////////////////////////
    // ****** Initialization ****** //
    //////////////////////////////////

    @Override
    protected NotifiableInventory<AEFluidKey> createTank(int initialCapacity, int slots, Object... args) {
        var storage = KeyInventory.fluids(1, initialCapacity);
        // start with the full amount
        storage.set(0, AEFluidKey.of(Fluids.WATER), initialCapacity);
        // allow both importing and exporting from the tank
        var waterTank = new NotifiableInventory<>(this, storage, io, IO.BOTH);
        // don't allow external filling
        waterTank.setFilter(key -> false);
        return waterTank;
    }

    //////////////////////////////////
    // ******** Fill Water ******** //
    //////////////////////////////////

    protected boolean isWaterFull() {
        return tank.storage.amountAt(0) >= tank.storage.slotLimit();
    }

    @Override
    protected void updateTankSubscription() {
        if (isWorkingEnabled() && !isWaterFull()) {
            autoIOSubs = subscribeServerTick(autoIOSubs, autoIOMonitor, 20);
        } else if (autoIOSubs != null) {
            autoIOSubs.unsubscribe();
            autoIOSubs = null;
        }
    }

    @Override
    protected void autoIO() {
        // replace with refilling water tank
        if (!isWaterFull()) tank.storage.set(0, AEFluidKey.of(Fluids.WATER), tank.storage.slotLimit());
        updateTankSubscription();
    }

    // By returning false here, we don't allow shift-clicking
    // with a screwdriver to swap the IO.
    @Override
    public boolean swapIO() {
        return false;
    }
}
