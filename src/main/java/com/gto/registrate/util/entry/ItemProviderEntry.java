package com.gto.registrate.util.entry;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public class ItemProviderEntry<T extends ItemLike> extends RegistryEntry<T> implements ItemLike {

    public ItemProviderEntry(ResourceKey<T> key) {
        super(key);
    }

    public ItemStack asStack() {
        return new ItemStack(this);
    }

    public ItemStack asStack(int count) {
        return new ItemStack(this, count);
    }

    public boolean isIn(ItemStack stack) {
        return stack.getItem() == asItem();
    }

    @Override
    public <R> boolean is(R item) {
        return asItem() == item;
    }

    @Override
    public Item asItem() {
        return get().asItem();
    }
}
