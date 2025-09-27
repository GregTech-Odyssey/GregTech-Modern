package com.gregtechceu.gtceu.api.capability.recipe.function;

import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface ItemConsumer {

    void accept(ItemStack itemStack, long amount);
}
