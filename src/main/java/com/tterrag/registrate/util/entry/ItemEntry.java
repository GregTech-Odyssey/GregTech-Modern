package com.tterrag.registrate.util.entry;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ItemEntry<T extends Item> extends ItemProviderEntry<T> {

    public ItemEntry(ResourceKey<T> key) {
        super(key);
    }

    public static <T extends Item> ItemEntry<T> cast(RegistryEntry<T> entry) {
        return RegistryEntry.cast(ItemEntry.class, entry);
    }

    @Override
    public ItemStack asStack() {
        return new ItemStack(value);
    }

    @Override
    public ItemStack asStack(int count) {
        return new ItemStack(value, count);
    }

    @Override
    public boolean isIn(ItemStack stack) {
        return stack.getItem() == value;
    }

    @Override
    public <R> boolean is(R item) {
        return value == item;
    }

    @Override
    public Item asItem() {
        return value;
    }
}
