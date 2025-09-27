package com.gregtechceu.gtceu.api.capability.recipe.function;

import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface ItemPredicate {

    boolean test(ItemStack itemStack, long amount);
}
