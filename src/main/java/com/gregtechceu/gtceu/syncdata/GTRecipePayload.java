package com.gregtechceu.gtceu.syncdata;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;

import com.lowdragmc.lowdraglib.syncdata.payload.ObjectTypedPayload;

import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

public class GTRecipePayload extends ObjectTypedPayload<GTRecipe> {

    @Nullable
    @Override
    public Tag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", payload.id.toString());
        tag.put("recipe",
                GTRecipeSerializer.CODEC.encodeStart(NbtOps.INSTANCE, payload).result().orElse(new CompoundTag()));
        tag.putInt("parallels", (int) payload.parallels);
        tag.putInt("ocLevel", payload.ocLevel);
        return tag;
    }

    @Override
    public void deserializeNBT(Tag tag) {
        if (tag instanceof CompoundTag compoundTag) {
            payload = GTRecipeSerializer.CODEC.parse(NbtOps.INSTANCE, compoundTag.get("recipe")).result().orElse(null);
            if (payload != null) {
                payload.id = new ResourceLocation(compoundTag.getString("id"));
                payload.parallels = compoundTag.contains("parallels") ? compoundTag.getInt("parallels") : 1;
                payload.ocLevel = compoundTag.getInt("ocLevel");
            }
        }
    }

    @Override
    public void writePayload(FriendlyByteBuf buf) {
        buf.writeResourceLocation(this.payload.id);
        GTRecipeSerializer.SERIALIZER.toNetwork(buf, this.payload);
        buf.writeInt((int) this.payload.parallels);
        buf.writeInt(this.payload.ocLevel);
    }

    @Override
    public void readPayload(FriendlyByteBuf buf) {
        var id = buf.readResourceLocation();
        if (buf.isReadable()) {
            this.payload = GTRecipeSerializer.SERIALIZER.fromNetwork(id, buf);
            if (buf.isReadable()) {
                this.payload.parallels = buf.readInt();
                this.payload.ocLevel = buf.readInt();
            }
        }
    }
}
