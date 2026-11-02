package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.recipe.handler.IO;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidType;

import appeng.api.stacks.AEFluidKey;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class PumpHatchPartMachine extends FluidHatchPartMachine {

    public PumpHatchPartMachine(MetaMachineBlockEntity holder, Object... args) {
        super(holder, 0, IO.OUT, FluidType.BUCKET_VOLUME, 1, args);
    }

    @Override
    protected NotifiableInventory<AEFluidKey> createTank(int initialCapacity, int slots, Object... args) {
        return super.createTank(initialCapacity, slots).setFilter(key -> key instanceof AEFluidKey fluidKey && fluidKey.getFluid() == Fluids.WATER);
    }

    // By returning false here, we don't allow shift-clicking
    // with a screwdriver to swap the IO.
    @Override
    public boolean swapIO() {
        return false;
    }
}
