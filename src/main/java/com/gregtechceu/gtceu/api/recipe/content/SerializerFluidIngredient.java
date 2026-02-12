package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;

public class SerializerFluidIngredient implements IContentSerializer<FluidIngredient> {

    public static SerializerFluidIngredient INSTANCE = new SerializerFluidIngredient();

    private SerializerFluidIngredient() {}

    @Override
    public void toNetwork(FriendlyByteBuf buf, FluidIngredient content) {
        content.toNetwork(buf);
    }

    @Override
    public FluidIngredient fromNetwork(FriendlyByteBuf buf) {
        return FluidIngredient.fromNetwork(buf);
    }

    @Override
    public FluidIngredient fromJson(JsonElement json) {
        return FluidIngredient.fromJson(json);
    }

    @Override
    public JsonElement toJson(FluidIngredient content) {
        return content.toJson();
    }

    @Override
    public Tag toNbt(FluidIngredient content) {
        return content.toNbt();
    }

    @Override
    public FluidIngredient fromNbt(Tag tag) {
        return FluidIngredient.fromNbt((CompoundTag) tag);
    }

    @Override
    public FluidIngredient defaultValue() {
        return FluidIngredient.EMPTY;
    }

    @Override
    public Codec<FluidIngredient> codec() {
        return FluidIngredient.CODEC;
    }
}
