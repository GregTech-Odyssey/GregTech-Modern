package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.uipro.styletemplate.MachineEra;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidType;

import appeng.api.stacks.AEFluidKey;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SteamHatchPartMachine extends FluidHatchPartMachine {

    public static final int INITIAL_TANK_CAPACITY = 64 * FluidType.BUCKET_VOLUME;
    public static final boolean IS_STEEL = ConfigHolder.INSTANCE.machines.steelSteamMultiblocks;

    public SteamHatchPartMachine(MetaMachineBlockEntity holder) {
        this(holder, INITIAL_TANK_CAPACITY);
    }

    protected SteamHatchPartMachine(MetaMachineBlockEntity holder, int capacity) {
        super(holder, 0, IO.IN, capacity, 1);
    }

    @Override
    protected NotifiableInventory<AEFluidKey> createTank(int initialCapacity, int slots, Object... args) {
        return super.createTank(initialCapacity, slots)
                .setFilter(key -> key instanceof AEFluidKey fluidKey && fluidKey.getFluid() == GTMaterials.Steam.getFluid());
    }

    @Override
    public ResourceLocation getWindowSkin() {
        return MachineEra.steam(IS_STEEL).getSkin();
    }

    // By returning false here, we don't allow shift-clicking
    // with a screwdriver to swap the IO, since this is a
    // hatch that only allows steam in, not
    // a steam version of an input/output hatch
    @Override
    public boolean swapIO() {
        return false;
    }
}
