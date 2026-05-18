package com.gregtechceu.gtceu.api.recipe.content;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

public interface IContentSerializer<T extends ContentInner> {

    void toNetwork(FriendlyByteBuf buf, T content);

    T fromNetwork(FriendlyByteBuf buf);

    Tag toNbt(T content);

    T fromNbt(Tag tag);

    @Nullable
   default Tag toNbtContent(Content<T> content){
        if ( !content.isEmpty()) {
            var t = new CompoundTag();
            addChance(t, content);
            t.put("content", content.inner.toNbt());
            t.putLong("amount", content.amount);
            return t;
        }
        return null;
    }

    @Nullable
    default Content<T> fromNbtContent(Tag tag){
        if (tag instanceof CompoundTag compoundTag && compoundTag.tags.get("content") instanceof CompoundTag content) {
            var ingredient = fromNbt(content);
            if (ingredient instanceof ContentInner inner && !inner.isEmpty()) return new Content<>(ingredient, compoundTag.tags.get("amount") instanceof LongTag longTag ? longTag.getAsLong() : ingredient.amount, getChance(compoundTag), getTierChanceBoost(compoundTag));
        }
        return null;
    }

    default void toNetworkContent(FriendlyByteBuf buf, Content<T> content) {
        T inner = content.inner;
        toNetwork(buf, inner);
        buf.writeVarLong(content.amount);
        buf.writeVarInt(content.chance);
        buf.writeVarInt(content.tierChanceBoost);
    }

    default Content<T> fromNetworkContent(FriendlyByteBuf buf) {
        T inner = fromNetwork(buf);
        long amount = buf.readVarLong();
        int chance = buf.readVarInt();
        int tierChanceBoost = buf.readVarInt();
        return new Content<>(inner, amount,chance, tierChanceBoost);
    }

    private static int getChance(CompoundTag tag) {
        if (tag.tags.get("chance") instanceof IntTag chance) {
            return chance.getAsInt();
        }
        return Content.MAX_CHANCE;
    }

    private static int getTierChanceBoost(CompoundTag tag) {
        if (tag.tags.get("tierChanceBoost") instanceof IntTag tierChanceBoost) {
            return tierChanceBoost.getAsInt();
        }
        return 0;
    }

    private static void addChance(CompoundTag tag, Content<?> content) {
        if (content.chance != Content.MAX_CHANCE) {
            tag.putInt("chance", content.chance);
            if (content.tierChanceBoost != 0) {
                tag.putInt("tierChanceBoost", content.tierChanceBoost);
            }
        }
    }
}
