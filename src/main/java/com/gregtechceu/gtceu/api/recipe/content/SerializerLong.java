package com.gregtechceu.gtceu.api.recipe.content;

import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;

public class SerializerLong implements IContentSerializer<Long> {

    public static SerializerLong INSTANCE = new SerializerLong();

    private SerializerLong() {}

    @Override
    public void toNetwork(FriendlyByteBuf buf, Long content) {
        buf.writeVarLong(content);
    }

    @Override
    public Long fromNetwork(FriendlyByteBuf buf) {
        return buf.readVarLong();
    }

    @Override
    public Long fromJson(JsonElement json) {
        return json.getAsLong();
    }

    @Override
    public JsonElement toJson(Long content) {
        return new JsonPrimitive(content);
    }

    @Override
    public Tag toNbt(Long content) {
        return LongTag.valueOf(content);
    }

    @Override
    public Long fromNbt(Tag tag) {
        return ((LongTag) tag).getAsLong();
    }

    @Override
    public Long defaultValue() {
        return 0L;
    }

    @Override
    public Codec<Long> codec() {
        return Codec.LONG;
    }
}
