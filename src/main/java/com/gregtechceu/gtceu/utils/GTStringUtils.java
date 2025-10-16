package com.gregtechceu.gtceu.utils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

public class GTStringUtils {

    /**
     * Better implementation of {@link ItemStack#toString()} which respects the stack-aware
     * {@link net.minecraft.world.item.Item#getDescriptionId(ItemStack)} method.
     *
     * @param stack the stack to convert
     * @return the string form of the stack
     */
    @NotNull
    public static String itemStackToString(@NotNull ItemStack stack) {
        ResourceLocation itemId = GTUtil.ITEM_ID.apply(stack.getItem());
        return stack.getCount() + "x_" + itemId.getNamespace() + "_" + itemId.getPath();
    }

    @NotNull
    public static String fluidStackToString(@NotNull FluidStack stack) {
        ResourceLocation fluidId = GTUtil.FLUID_ID.apply(stack.getFluid());
        return stack.getAmount() + "x_" + fluidId.getNamespace() + "_" + fluidId.getPath();
    }
}
