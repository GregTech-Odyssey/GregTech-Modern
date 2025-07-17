package com.gregtechceu.gtceu.api.misc.forge;

import com.gregtechceu.gtceu.api.capability.IThermalFluidHandlerItemStack;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;

import org.jetbrains.annotations.NotNull;

public class ThermalFluidHandlerItemStack extends FluidHandlerItemStack implements IThermalFluidHandlerItemStack {

    private final int maxFluidTemperature;
    private final boolean gasProof;
    private final boolean acidProof;
    private final boolean cryoProof;
    private final boolean plasmaProof;

    /**
     * @param container The container itemStack, data is stored on it directly as NBT.
     * @param capacity  The maximum capacity of this fluid tank.
     */
    public ThermalFluidHandlerItemStack(@NotNull ItemStack container, int capacity, int maxFluidTemperature, boolean gasProof, boolean acidProof, boolean cryoProof, boolean plasmaProof) {
        super(container, capacity);
        this.maxFluidTemperature = maxFluidTemperature;
        this.gasProof = gasProof;
        this.acidProof = acidProof;
        this.cryoProof = cryoProof;
        this.plasmaProof = plasmaProof;
    }

    @Override
    public boolean canFillFluidType(FluidStack fluid) {
        return IThermalFluidHandlerItemStack.super.canFillFluidType(fluid);
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
