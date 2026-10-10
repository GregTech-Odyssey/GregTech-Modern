package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
import com.gregtechceu.gtceu.datasynclib.GTDataFixer;
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

import com.gto.datasynclib.util.ValueCodecs;
import com.gto.datasynclib.datastream.codec.JavaOps;
import com.gto.datasynclib.datastream.codec.ValueOps;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@Deprecated(since = "0.6.0", forRemoval = true)
@ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
final class LegacyRecipeCodec {

    private LegacyRecipeCodec() {}

    static ContentList items(Object data, ValueOps ops) {
        var b = new ContentList.Builder();
        if (!ops.isList(data)) return b.build();
        for (var entry : ops.getList(data)) {
            if (!ops.isList(entry)) continue;
            var list = ops.getList(entry);
            if (list.isEmpty()) continue;
            long amount = ops.getLong(list, 0);
            int chance = ops.isNull(list.get(1)) ? ContentList.MAX_CHANCE : ops.getInt(list, 1);
            int boost = ops.isNull(list.get(2)) ? 0 : ops.getInt(list, 2);
            var inner = list.get(3);
            var ingredient = itemFromData(inner, ops);
            if (ingredient == null || amount <= 0) continue;
            b.add(ingredient, amount, chance, boost, unitOf(inner, amount, ops));
        }
        return b.build();
    }

    static ContentList fluids(Object data, ValueOps ops) {
        var b = new ContentList.Builder();
        if (!ops.isList(data)) return b.build();
        for (var entry : ops.getList(data)) {
            if (!ops.isList(entry)) continue;
            var list = ops.getList(entry);
            if (list.isEmpty()) continue;
            long amount = ops.getLong(list, 0);
            int chance = ops.isNull(list.get(1)) ? ContentList.MAX_CHANCE : ops.getInt(list, 1);
            int boost = ops.isNull(list.get(2)) ? 0 : ops.getInt(list, 2);
            var inner = list.get(3);
            var ingredient = fluidFromData(inner, ops);
            if (ingredient == null || amount <= 0) continue;
            b.add(ingredient, amount, chance, boost, unitOf(inner, amount, ops));
        }
        return b.build();
    }

    private static long unitOf(Object inner, long fallback, ValueOps ops) {
        if (ops.isStringMap(inner)) {
            var map = ops.getStringMap(inner);
            var a = map.get("a");
            if (a != null) {
                long value = ops.getLong(a);
                return value > 0 ? value : fallback;
            }
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
    private static KeyIngredient itemFromData(Object data, ValueOps ops) {
        if (ops.isNull(data)) return null;
        if (ops.isByte(data)) return KeyIngredient.circuit(ops.getByte(data));
        if (!ops.isStringMap(data)) return null;
        var map = ops.getStringMap(data);
        var item = map.get("i");
        if (item != null) return legacyItem(ops.getString(item));
        var t = map.get("t");
        if (t != null) return KeyIngredient.itemTag(TagKey.create(Registries.ITEM, GTUtil.getResourceLocation(ops.getString(t))));
        var j = map.get("j");
        if (j != null) {
            var inner = Ingredient.fromJson(JavaOps.INSTANCE.convertTo(JsonOps.INSTANCE, j));
            return inner.isEmpty() ? null : KeyIngredient.of(inner);
        }
        return null;
    }

    @Nullable
    private static KeyIngredient fluidFromData(Object data, ValueOps ops) {
        if (ops.isNull(data)) return null;
        if (!ops.isStringMap(data)) return null;
        var map = ops.getStringMap(data);
        var nbtData = map.get("n");
        var nbt = nbtData != null && ops.isCustom(nbtData) ? ValueCodecs.COMPOUND_TAG.decode(ops, nbtData) : null;
        var fluid = map.get("f");
        if (fluid != null) return legacyFluid(ops.getString(fluid), nbt);
        var t = map.get("t");
        if (ops.isString(t)) return KeyIngredient.fluidTag(TagKey.create(Registries.FLUID, GTUtil.getResourceLocation(ops.getString(t))), nbt);
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
