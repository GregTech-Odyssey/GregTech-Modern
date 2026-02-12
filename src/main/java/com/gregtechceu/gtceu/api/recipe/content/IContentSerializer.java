package com.gregtechceu.gtceu.api.recipe.content;

import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;

public interface IContentSerializer<T> {

    Codec<T> codec();

    void toNetwork(FriendlyByteBuf buf, T content);

    T fromNetwork(FriendlyByteBuf buf);

    T fromJson(JsonElement json);

    JsonElement toJson(T content);

    Tag toNbt(T content);

    T fromNbt(Tag tag);

    T defaultValue();

    @SuppressWarnings("unchecked")
    default void toNetworkContent(FriendlyByteBuf buf, Content content) {
        T inner = (T) content.inner;
        toNetwork(buf, inner);
        buf.writeVarInt(content.chance);
        buf.writeVarInt(content.tierChanceBoost);
    }

    default Content fromNetworkContent(FriendlyByteBuf buf) {
        T inner = fromNetwork(buf);
        int chance = buf.readVarInt();
        int tierChanceBoost = buf.readVarInt();
        return new Content(inner, chance, tierChanceBoost);
    }
}
