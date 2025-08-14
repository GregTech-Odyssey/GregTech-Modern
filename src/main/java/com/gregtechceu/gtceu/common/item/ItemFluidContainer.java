package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.item.component.IRecipeRemainder;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;

public class ItemFluidContainer implements IRecipeRemainder {

    @Override
    public ItemStack getRecipeRemained(ItemStack itemStack) {
        return FluidUtil.getFluidHandler(itemStack).map(handler -> {
            int contained = FluidType.BUCKET_VOLUME;
            if (handler instanceof FluidHandlerItemStack fluidHandler) {
                contained = Math.max(contained, fluidHandler.getFluid().getAmount());
            }
            var drained = handler.drain(contained, FluidAction.SIMULATE);
            if (drained.getAmount() != contained) return ItemStack.EMPTY;
            handler.drain(contained, FluidAction.EXECUTE);
            return handler.getContainer();
        }).orElse(itemStack);
    }
}
