package com.gregtechceu.gtceu.core.mixins.ldlib;

import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;

import com.lowdragmc.lowdraglib.syncdata.SyncUtils;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(SyncUtils.class)
public class SyncUtilsMixin {

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public static boolean isChanged(@NotNull Object oldValue, @NotNull Object newValue) {
        if (oldValue instanceof ItemStack itemStack) {
            if (!(newValue instanceof ItemStack newStack)) {
                return true;
            } else {
                return !ItemStackHashStrategy.ALL.equals(itemStack, newStack);
            }
        } else if (oldValue instanceof FluidStack fluidStack) {
            if (!(newValue instanceof FluidStack newStack)) {
                return true;
            } else {
                return !fluidStack.isFluidStackIdentical(newStack);
            }
        } else {
            return !oldValue.equals(newValue);
        }
    }
}
