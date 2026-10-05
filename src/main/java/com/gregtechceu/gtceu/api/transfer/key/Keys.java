package com.gregtechceu.gtceu.api.transfer.key;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

public final class Keys {

    private Keys() {}

    public static @Nullable AEFluidKey fluid(FluidStack stack) {
        if (stack.isEmpty()) return null;
        var tag = stack.getTag();
        return tag == null ? AEFluidKey.ofSource(stack.getRawFluid()) : tagged(stack.getRawFluid(), tag);
    }

    public static @Nullable AEFluidKey fluidType(FluidStack stack) {
        Fluid fluid = stack.getRawFluid();
        if (fluid == Fluids.EMPTY) return null;
        var tag = stack.getTag();
        return tag == null ? AEFluidKey.ofSource(fluid) : tagged(fluid, tag);
    }

    private static AEFluidKey tagged(Fluid fluid, CompoundTag tag) {
        return AEFluidKey.of(source(fluid), tag);
    }

    public static Fluid source(Fluid fluid) {
        return fluid instanceof FlowingFluid flowing && fluid != flowing.getSource() ? flowing.getSource() : fluid;
    }

    public static ItemStack toStack(AEItemKey key, long amount) {
        return key.toStack(amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount);
    }

    public static FluidStack toFluidStack(AEFluidKey key, long amount) {
        return key.toStack(amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount);
    }

    public static ItemStack displayStack(@Nullable AEItemKey key) {
        return key == null ? ItemStack.EMPTY : key.getReadOnlyStack();
    }

    public static FluidStack displayFluid(@Nullable AEFluidKey key) {
        return key == null ? FluidStack.EMPTY : key.getReadOnlyStack();
    }

    public static ItemStack toStack(@Nullable AEKey key, long amount) {
        return key instanceof AEItemKey itemKey && amount > 0 ? toStack(itemKey, amount) : ItemStack.EMPTY;
    }

    public static int saturatedInt(long amount) {
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : amount < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) amount;
    }

    public static long add(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? (a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE) : r;
    }

    public static long multiply(long a, long b) {
        long hi = Math.multiplyHigh(a, b);
        long lo = a * b;
        if ((hi == 0 && lo >= 0) || (hi == -1 && lo < 0)) return lo;
        return (a ^ b) < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
    }
}
