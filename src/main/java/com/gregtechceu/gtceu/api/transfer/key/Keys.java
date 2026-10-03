package com.gregtechceu.gtceu.api.transfer.key;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

public final class Keys {

    private Keys() {}

    private static final ConcurrentHashMap<Item, AEItemKey> DEPLETABLE = new ConcurrentHashMap<>();

    public static @Nullable AEItemKey item(ItemStack stack) {
        if (stack.getCount() <= 0) return null;
        return AEItemKey.of(stack);
    }

    public static @Nullable AEItemKey itemType(ItemStack stack) {
        return AEItemKey.of(stack);
    }

    public static AEItemKey item(ItemLike itemLike) {
        Item item = itemLike.asItem();
        if (!item.canBeDepleted()) return AEItemKey.of(item);
        return DEPLETABLE.computeIfAbsent(item, i -> AEItemKey.of(new ItemStack(i)));
    }

    public static @Nullable AEFluidKey fluid(FluidStack stack) {
        if (stack.isEmpty()) return null;
        Fluid fluid = source(stack.getRawFluid());
        var tag = stack.getTag();
        return tag == null || tag.isEmpty() ? AEFluidKey.of(fluid) : AEFluidKey.of(fluid, tag);
    }

    public static @Nullable AEFluidKey fluidType(FluidStack stack) {
        Fluid fluid = stack.getRawFluid();
        if (fluid == Fluids.EMPTY) return null;
        fluid = source(fluid);
        var tag = stack.getTag();
        return tag == null || tag.isEmpty() ? AEFluidKey.of(fluid) : AEFluidKey.of(fluid, tag);
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
