package com.gregtechceu.gtceu.api.misc.forge;

import com.gregtechceu.gtceu.api.capability.IThermalFluidHandlerItemStack;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStackSimple;

import org.jetbrains.annotations.NotNull;

public class SimpleThermalFluidHandlerItemStack extends FluidHandlerItemStackSimple implements IThermalFluidHandlerItemStack {

    private final int maxFluidTemperature;
    private final boolean gasProof;
    private final boolean acidProof;
    private final boolean cryoProof;
    private final boolean plasmaProof;

    public SimpleThermalFluidHandlerItemStack(@NotNull ItemStack container, int capacity, int maxFluidTemperature, boolean gasProof, boolean acidProof, boolean cryoProof, boolean plasmaProof) {
        super(container, capacity);
        this.maxFluidTemperature = maxFluidTemperature;
        this.gasProof = gasProof;
        this.acidProof = acidProof;
        this.cryoProof = cryoProof;
        this.plasmaProof = plasmaProof;
    }

    public int getMaxFluidTemperature() {
        return this.maxFluidTemperature;
    }

    public boolean isGasProof() {
        return this.gasProof;
    }

    public boolean isAcidProof() {
        return this.acidProof;
    }

    public boolean isCryoProof() {
        return this.cryoProof;
    }

    public boolean isPlasmaProof() {
        return this.plasmaProof;
    }
}
