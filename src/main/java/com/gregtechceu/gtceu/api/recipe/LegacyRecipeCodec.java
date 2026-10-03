package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;

import com.gto.datasynclib.datastream.data.ByteData;
import com.gto.datasynclib.datastream.data.Data;
import com.gto.datasynclib.datastream.data.DataOps;
import com.gto.datasynclib.datastream.data.StringData;
import com.gto.datasynclib.datastream.data.StringMapData;
import com.gto.datasynclib.util.DataCodecs;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@Deprecated(since = "0.6.0", forRemoval = true)
@ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
final class LegacyRecipeCodec {

    private LegacyRecipeCodec() {}

    static ContentList items(Data data, int dataVersion) {
        var b = new ContentList.Builder();
        for (var entry : data.getList()) {
            var list = entry.getList();
            if (list.isEmpty()) continue;
            long amount = list.getFirst().getLong();
            int chance = list.get(1).isNull() ? ContentList.MAX_CHANCE : list.get(1).getInt();
            int boost = list.get(2).isNull() ? 0 : list.get(2).getInt();
            var inner = list.get(3);
            var ingredient = itemFromData(inner, dataVersion);
            if (ingredient == null || amount <= 0) continue;
            b.add(ingredient, amount, chance, boost, unitOf(inner, amount));
        }
        return b.build();
    }

    static ContentList fluids(Data data, int dataVersion) {
        var b = new ContentList.Builder();
        for (var entry : data.getList()) {
            var list = entry.getList();
            if (list.isEmpty()) continue;
            long amount = list.getFirst().getLong();
            int chance = list.get(1).isNull() ? ContentList.MAX_CHANCE : list.get(1).getInt();
            int boost = list.get(2).isNull() ? 0 : list.get(2).getInt();
            var inner = list.get(3);
            var ingredient = fluidFromData(inner, dataVersion);
            if (ingredient == null || amount <= 0) continue;
            b.add(ingredient, amount, chance, boost, unitOf(inner, amount));
        }
        return b.build();
    }

    private static long unitOf(Data inner, long fallback) {
        if (inner instanceof StringMapData map && map.get("a") != null) {
            long a = map.get("a").getLong();
            return a > 0 ? a : fallback;
        }
        return 1;
    }

    @Nullable
    private static KeyIngredient legacyItem(String id) {
        var item = GTUtil.ITEM_VALUE.apply(GTUtil.getResourceLocation(id));
        return item == null || item == Items.AIR ? null : KeyIngredient.item(item);
    }

    @Nullable
    private static KeyIngredient legacyFluid(String id, @Nullable CompoundTag nbt) {
        var fluid = GTUtil.FLUID_VALUE.apply(GTUtil.getResourceLocation(id));
        return fluid == null || fluid == Fluids.EMPTY ? null : KeyIngredient.of(fluid, nbt);
    }

    @Nullable
    private static KeyIngredient itemFromData(Data data, int dataVersion) {
        if (data.isNull()) return null;
        if (data instanceof ByteData(byte i)) return KeyIngredient.circuit(i);
        var map = data.getStringMap();
        var item = map.get("i");
        if (item != null) return legacyItem(item.getString());
        var t = map.get("t");
        if (t != null) return KeyIngredient.itemTag(TagKey.create(Registries.ITEM, GTUtil.getResourceLocation(t.getString())));
        var j = map.get("j");
        if (j != null) {
            var inner = Ingredient.fromJson(DataOps.INSTANCE.convertTo(JsonOps.INSTANCE, j));
            return inner.isEmpty() ? null : KeyIngredient.of(inner);
        }
        return null;
    }

    @Nullable
    private static KeyIngredient fluidFromData(Data data, int dataVersion) {
        if (data.isNull()) return null;
        var map = data.getStringMap();
        var nbt = map.get("n") instanceof StringMapData n ? DataCodecs.COMPOUND_TAG_CODEC.decode(n, dataVersion) : null;
        var fluid = map.get("f");
        if (fluid != null) return legacyFluid(fluid.getString(), nbt);
        if (map.get("t") instanceof StringData(String s)) return KeyIngredient.fluidTag(TagKey.create(Registries.FLUID, GTUtil.getResourceLocation(s)), nbt);
        return null;
    }

    static ContentList itemsFromNbt(CompoundTag io) {
        var b = new ContentList.Builder();
        if (io.get("item") instanceof ListTag list) {
            for (var t : list) {
                if (!(t instanceof CompoundTag c) || !(c.get("content") instanceof CompoundTag content)) continue;
                long amount = content.getLong("count");
                KeyIngredient ingredient = null;
                if (content.get("Configuration") instanceof NumericTag n) {
                    ingredient = KeyIngredient.circuit(n.getAsInt());
                    amount = 1;
                } else if (content.get("item") instanceof StringTag s) {
                    ingredient = legacyItem(s.getAsString());
                } else if (content.get("tag") instanceof StringTag s) {
                    ingredient = KeyIngredient.itemTag(TagKey.create(Registries.ITEM, GTUtil.getResourceLocation(s.getAsString())));
                } else if (content.get("ingredient") instanceof Tag in) {
                    var inner = Ingredient.fromJson(NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, in));
                    if (!inner.isEmpty()) ingredient = KeyIngredient.of(inner);
                }
                if (ingredient != null && amount > 0) b.add(ingredient, amount, chance(c), boost(c));
            }
        }
        return b.build();
    }

    static ContentList fluidsFromNbt(CompoundTag io) {
        var b = new ContentList.Builder();
        if (io.get("fluid") instanceof ListTag list) {
            for (var t : list) {
                if (!(t instanceof CompoundTag c) || !(c.get("content") instanceof CompoundTag content) || content.getBoolean("empty")) continue;
                long amount = content.getLong("amount");
                var nbt = content.get("nbt") instanceof CompoundTag n ? n : null;
                KeyIngredient ingredient = null;
                if (content.get("fluid") instanceof StringTag s) {
                    ingredient = legacyFluid(s.getAsString(), nbt);
                } else if (content.get("tag") instanceof StringTag s) {
                    ingredient = KeyIngredient.fluidTag(TagKey.create(Registries.FLUID, GTUtil.getResourceLocation(s.getAsString())), nbt);
                }
                if (ingredient != null && amount > 0) b.add(ingredient, amount, chance(c), boost(c));
            }
        }
        return b.build();
    }

    private static int chance(CompoundTag tag) {
        return tag.get("chance") instanceof IntTag chance ? chance.getAsInt() : ContentList.MAX_CHANCE;
    }

    private static int boost(CompoundTag tag) {
        return tag.get("tierChanceBoost") instanceof IntTag boost ? boost.getAsInt() : 0;
    }
}
