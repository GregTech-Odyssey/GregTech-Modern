package com.gregtechceu.gtceu.syncdata;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import com.lowdragmc.lowdraglib.syncdata.payload.ObjectTypedPayload;

import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import org.jetbrains.annotations.Nullable;

public class GTRecipePayload extends ObjectTypedPayload<GTRecipe> {

    @Nullable
    @Override
    public Tag serializeNBT() {
        return GTRecipe.toNbt(this.payload);
    }

    @Override
    public void deserializeNBT(Tag tag) {
        this.payload = GTRecipe.fromNbt(tag);
    }

    @Override
    public void writePayload(FriendlyByteBuf buf) {
        GTRecipe.STREAM_CODEC.encode(this.payload, buf);
    }

    @Override
    public void readPayload(FriendlyByteBuf buf) {
        this.payload = GTRecipe.STREAM_CODEC.decode(buf);
    }
}
