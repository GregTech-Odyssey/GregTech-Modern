package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datasream.data.Data;

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
    public Data toData(ItemIngredient content) {
        return content.toData();
    }

    @Override
    public ItemIngredient fromData(Data data) {
        return ItemIngredient.fromData(data);
    }
}
