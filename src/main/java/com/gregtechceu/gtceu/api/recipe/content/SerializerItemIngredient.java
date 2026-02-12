package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

public class SerializerItemIngredient implements IContentSerializer<ItemIngredient> {

    public static final Codec<ItemIngredient> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> ItemIngredient.fromJson(dynamic.convert(JsonOps.INSTANCE).getValue()),
            ingredient -> new Dynamic<>(JsonOps.INSTANCE, ingredient.toJson()));

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
    public ItemIngredient fromJson(JsonElement json) {
        return ItemIngredient.fromJson(json);
    }

    @Override
    public JsonElement toJson(ItemIngredient content) {
        return content.toJson();
    }

    @Override
    public Tag toNbt(ItemIngredient content) {
        return content.toNbt();
    }

    @Override
    public ItemIngredient fromNbt(Tag tag) {
        return ItemIngredient.fromNbt((CompoundTag) tag);
    }

    @Override
    public ItemIngredient defaultValue() {
        return ItemIngredient.EMPTY;
    }

    @Override
    public Codec<ItemIngredient> codec() {
        return CODEC;
    }
}
