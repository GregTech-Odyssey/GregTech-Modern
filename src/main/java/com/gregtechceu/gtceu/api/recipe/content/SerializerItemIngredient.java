package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

public class SerializerItemIngredient implements IContentSerializer<ItemIngredient> {

    public static SerializerItemIngredient INSTANCE = new SerializerItemIngredient();

    private SerializerItemIngredient() {}

    @Override
    public void toNetwork(FriendlyByteBuf buf, ItemIngredient content) {
        content.toNetwork(buf);
    }

    @Override
    public ItemIngredient fromNetwork(FriendlyByteBuf buf) {
        return ItemIngredient.fromNetwork(buf);
    }

    @Override
    public Tag toNbt(ItemIngredient content) {
        return content.toNbt();
    }

    @Override
    public ItemIngredient fromNbt(Tag tag) {
        return ItemIngredient.fromNbt((CompoundTag) tag);
    }
}
