package com.gregtechceu.gtceu.api.recipe.content;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datasream.data.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IContentSerializer<T extends ContentInner> {

    void toNetwork(FriendlyByteBuf buf, T content);

    T fromNetwork(FriendlyByteBuf buf);

    Data toData(T content);

    T fromData(Data data);

    @NotNull
    default Data toDataContent(Content<T> content) {
        if (!content.isEmpty()) {
            var t = new ListData(4);
            t.add(LongData.valueOf(content.amount));
            if (content.chance == Content.MAX_CHANCE) {
                t.addNull();
            } else {
                t.add(IntData.valueOf(content.chance));
            }
            if (content.tierChanceBoost == 0) {
                t.addNull();
            } else {
                t.add(IntData.valueOf(content.tierChanceBoost));
            }
            t.add(toData(content.inner));
            return t;
        }
        return NullData.INSTANCE;
    }

    @Nullable
    default Content<T> fromDataContent(Data data, int dataVersion) {
        var list = data.getList();
        if (list.isEmpty()) return null;
        var amount = list.getFirst().getLong();
        var chanceData = list.get(1);
        var chance = chanceData.isNull() ? Content.MAX_CHANCE : chanceData.getInt();
        var tierChanceBoostData = list.get(2);
        var tierChanceBoost = tierChanceBoostData.isNull() ? 0 : tierChanceBoostData.getInt();
        var inner = fromData(list.get(3));
        return new Content<>(inner, amount, chance, tierChanceBoost);
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
        return new Content<>(inner, amount, chance, tierChanceBoost);
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
