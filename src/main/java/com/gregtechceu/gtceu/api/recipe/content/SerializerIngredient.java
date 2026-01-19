package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;

public class SerializerIngredient implements IContentSerializer<Ingredient> {

    public static final Codec<Ingredient> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> Ingredient.fromJson(dynamic.convert(JsonOps.INSTANCE).getValue()),
            ingredient -> new Dynamic<>(JsonOps.INSTANCE, ingredient.toJson()));

    public static SerializerIngredient INSTANCE = new SerializerIngredient();

    private SerializerIngredient() {}

    @Override
    public void toNetwork(FriendlyByteBuf buf, Ingredient content) {
        content.toNetwork(buf);
    }

    @Override
    public Ingredient fromNetwork(FriendlyByteBuf buf) {
        return Ingredient.fromNetwork(buf);
    }

    @Override
    public Ingredient fromJson(JsonElement json) {
        return Ingredient.fromJson(json);
    }

    @Override
    public JsonElement toJson(Ingredient content) {
        return content.toJson();
    }

    @Override
    public Tag toNbt(Ingredient content) {
        return JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, this.toJson(content));
    }

    @Override
    public Ingredient fromNbt(Tag tag) {
        var json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
        return fromJson(json);
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Ingredient of(Object o) {
        return switch (o) {
            case Ingredient ingredient -> ingredient;
            case ItemStack itemStack -> SizedIngredient.create(itemStack);
            case ItemLike itemLike -> Ingredient.of(itemLike);
            case TagKey tag -> Ingredient.of(tag);
            case null, default -> Ingredient.EMPTY;
        };
    }

    @Override
    public Ingredient defaultValue() {
        return Ingredient.EMPTY;
    }

    @Override
    public Codec<Ingredient> codec() {
        return CODEC;
    }
}
