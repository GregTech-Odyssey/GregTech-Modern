package com.gregtechceu.gtceu.api.misc.forge;

import com.gregtechceu.gtceu.api.capability.IThermalFluidHandlerItemStack;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStackSimple;

import org.jetbrains.annotations.NotNull;

public class SimpleThermalFluidHandlerItemStack extends FluidHandlerItemStackSimple implements IThermalFluidHandlerItemStack {

    private final boolean gasProof;
    private final boolean plasmaProof;

    public SimpleThermalFluidHandlerItemStack(@NotNull ItemStack container, int capacity, boolean gasProof, boolean plasmaProof) {
        super(container, capacity);
        this.gasProof = gasProof;
        this.plasmaProof = plasmaProof;
    }

    public boolean isGasProof() {
        return this.gasProof;
    }

    public boolean isPlasmaProof() {
        return this.plasmaProof;
    }
}
