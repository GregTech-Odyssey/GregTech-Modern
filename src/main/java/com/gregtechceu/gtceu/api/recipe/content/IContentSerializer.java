package com.gregtechceu.gtceu.api.recipe.content;

import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

public interface IContentSerializer<T extends ContentInner> {

    void toNetwork(FriendlyByteBuf buf, T content);

    T fromNetwork(FriendlyByteBuf buf);

    Tag toNbt(T content);

    T fromNbt(Tag tag);

    default void toNetworkContent(FriendlyByteBuf buf, Content<T> content) {
        T inner = content.inner;
        toNetwork(buf, inner);
        buf.writeVarInt(content.chance);
        buf.writeVarInt(content.tierChanceBoost);
    }

    default Content<T> fromNetworkContent(FriendlyByteBuf buf) {
        T inner = fromNetwork(buf);
        int chance = buf.readVarInt();
        int tierChanceBoost = buf.readVarInt();
        return new Content<>(inner, chance, tierChanceBoost);
    }
}
