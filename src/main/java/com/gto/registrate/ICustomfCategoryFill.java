package com.gto.registrate;

import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public interface ICustomfCategoryFill {

    void fillItemCategory(Consumer<ItemStack> consumer);
}
