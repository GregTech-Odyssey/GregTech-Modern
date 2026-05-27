package com.gregtechceu.gtceu.api.recipe.ingredient;

import com.gregtechceu.gtceu.api.recipe.content.ContentInner;
import com.gregtechceu.gtceu.core.mixins.StrictNBTIngredientAccessor;
import com.gregtechceu.gtceu.data.recipe.builder.ShapedRecipeBuilder;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.common.crafting.StrictNBTIngredient;

import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.datasream.data.*;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.Hash;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public class ItemIngredient extends ContentInner implements Predicate<ItemStack> {

    public static final ItemIngredient EMPTY = new ItemIngredient(Ingredient.of(), 0);

    public final Ingredient inner;

    @Getter
    private final boolean isEmpty;
    private final Ingredient.Value value;

    protected ItemIngredient(Ingredient inner, long amount) {
        super(amount);
        this.inner = inner;
        isEmpty = inner.isEmpty();
        value = isEmpty ? null : inner.getClass() == Ingredient.class ? inner.values[0] : null;
    }

    private ItemIngredient(ItemIngredient ingredient, long amount) {
        super(amount);
        this.inner = ingredient.inner;
        this.isEmpty = ingredient.isEmpty;
        this.value = ingredient.value;
        this.hashCode = ingredient.hashCode;
    }

    public int getAmount() {
        if (amount > 2147483647L) {
            return Integer.MAX_VALUE;
        } else {
            return (int) amount;
        }
    }

    public void toNetwork(FriendlyByteBuf buffer) {
        if (isEmpty) {
            buffer.writeByte(1);
            return;
        } else if (value instanceof Ingredient.ItemValue itemValue) {
            buffer.writeByte(2);
            buffer.writeInt(BuiltInRegistries.ITEM.getId(itemValue.item.getItem()));
        } else if (value instanceof Ingredient.TagValue tagValue) {
            buffer.writeByte(3);
            buffer.writeResourceLocation(tagValue.tag.location());
        } else {
            buffer.writeByte(4);
            inner.toNetwork(buffer);
        }
        buffer.writeVarLong(amount);
    }

    public static ItemIngredient fromNetwork(FriendlyByteBuf buffer) {
        var id = buffer.readByte();
        return switch (id) {
            case 1 -> EMPTY;
            case 2 -> new ItemIngredient(Ingredient.of(BuiltInRegistries.ITEM.byId(buffer.readInt())), buffer.readVarLong());
            case 3 -> new ItemIngredient(Ingredient.of(TagKey.create(Registries.ITEM, buffer.readResourceLocation())), buffer.readVarLong());
            case 4 -> new ItemIngredient(Ingredient.fromNetwork(buffer), buffer.readVarLong());
            default -> IntCircuitIngredient.CIRCUIT_INPUTS[-id];
        };
    }

    public Data toData() {
        if (isEmpty) return NullData.INSTANCE;
        var data = new MapData();
        switch (value) {
            case Ingredient.ItemValue itemValue -> data.putString("i", GTUtil.ITEM_ID.apply(itemValue.item.getItem()).toString());
            case Ingredient.TagValue tagValue -> data.putString("t", tagValue.tag.location().toString());
            case null, default -> data.put("j", JsonOps.INSTANCE.convertTo(DataOps.INSTANCE, inner.toJson()));
        }
        data.putLong("a", amount);
        return data;
    }

    public static ItemIngredient fromNbt(CompoundTag tag) {
        if (tag.tags.get(IntCircuitIngredient.Configuration) instanceof NumericTag numericTag) {
            return IntCircuitIngredient.CIRCUIT_INPUTS[numericTag.getAsInt()];
        }
        var amount = tag.getLong("count");
        var item = tag.tags.get("item");
        if (item != null) {
            return ItemIngredient.of(GTUtil.ITEM_VALUE.apply(GTUtil.getResourceLocation(item.getAsString())), amount);
        }
        var t = tag.tags.get("tag");
        if (t != null) {
            return ItemIngredient.of(TagKey.create(Registries.ITEM, GTUtil.getResourceLocation(t.getAsString())), amount);
        }
        var in = tag.tags.get("ingredient");
        if (in != null) {
            Ingredient inner = Ingredient.fromJson(NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, in));
            if (!inner.isEmpty()) {
                return new ItemIngredient(inner, amount);
            }
        }
        return EMPTY;
    }

    public static ItemIngredient fromData(Data data) {
        if (data.isNull()) return EMPTY;
        if (data instanceof ByteData(byte i)) {
            return IntCircuitIngredient.CIRCUIT_INPUTS[i];
        }
        var map = data.getMap();
        var amount = map.get("a").getLong();
        var item = map.get("i");
        if (item != null) {
            return ItemIngredient.of(GTUtil.ITEM_VALUE.apply(GTUtil.getResourceLocation(item.getString())), amount);
        }
        var t = map.get("t");
        if (t != null) {
            return ItemIngredient.of(TagKey.create(Registries.ITEM, GTUtil.getResourceLocation(t.getString())), amount);
        }
        var in = map.get("j");
        if (in != null) {
            Ingredient inner = Ingredient.fromJson(DataOps.INSTANCE.convertTo(JsonOps.INSTANCE, in));
            if (!inner.isEmpty()) {
                return new ItemIngredient(inner, amount);
            }
        }
        throw new IllegalArgumentException("Invalid ItemIngredient data: " + data);
    }

    public ItemIngredient copy(long amount) {
        return new ItemIngredient(this, amount);
    }

    @Override
    public Component getName() {
        if (isEmpty) return Component.literal("Item[empty]");
        if (value != null) {
            return getComponent(value);
        } else if (inner.values.length == 1) {
            return getComponent(inner.values[0]);
        }
        return Component.literal(inner.toString());
    }

    public static Component getComponent(Ingredient.Value value) {
        if (value instanceof Ingredient.TagValue tagValue) {
            return Component.literal("Tag[" + tagValue.tag.location() + "]");
        } else if (value instanceof Ingredient.ItemValue itemValue) {
            return itemValue.item.getDisplayName();
        }
        var component = Component.empty();
        value.getItems().forEach(i -> component.append(i.getDisplayName()));
        return component;
    }

    public boolean testItem(Item item) {
        if (isEmpty) return item == Items.AIR;
        Ingredient.Value value = this.value;
        if (value == null) {
            var values = inner.values;
            if (values.length > 0) {
                value = values[0];
            }
        }
        if (value instanceof Ingredient.TagValue tagValue) {
            return item.builtInRegistryHolder().is(tagValue.tag);
        } else if (value instanceof Ingredient.ItemValue itemValue) {
            return item == itemValue.item.getItem();
        }
        return true;
    }

    public boolean testAeKay(@NotNull AEItemKey key) {
        var item = key.getItem();
        if (isEmpty) return item == Items.AIR;
        if (value instanceof Ingredient.TagValue tagValue) {
            return item.builtInRegistryHolder().is(tagValue.tag);
        } else if (value instanceof Ingredient.ItemValue itemValue) {
            return item == itemValue.item.getItem();
        }
        return inner.test(key.getReadOnlyStack());
    }

    @Override
    public boolean test(@Nullable ItemStack stack) {
        if (stack == null) return false;
        var item = stack.getItem();
        if (isEmpty) return item == Items.AIR;
        if (value instanceof Ingredient.TagValue tagValue) {
            return item.builtInRegistryHolder().is(tagValue.tag);
        } else if (value instanceof Ingredient.ItemValue itemValue) {
            return item == itemValue.item.getItem();
        }
        return inner.test(stack);
    }

    @NotNull
    public ItemStack getItem() {
        if (value instanceof Ingredient.ItemValue itemValue) return itemValue.item;
        return ItemStack.EMPTY;
    }

    @NotNull
    public ItemStack getInnerItemStack() {
        var stacks = inner.getItems();
        if (stacks.length == 0) return ItemStack.EMPTY;
        return stacks[0];
    }

    // private ItemStack getItemStack() {
    // return getFirst(getStacks());
    // }
    //
    // private ItemStack[] getLatestStacks() {
    // if (changed) this.stacks = null;
    // return getStacks();
    // }
    //
    // private ItemStack[] getStacks() {
    // if (this.stacks == null) {
    // var stacks = ContentInner.getItems();
    // var length = stacks.length;
    // if (length < 1) {
    // this.stacks = EMPTY_STACKS;
    // } else if (length == 1) {
    // ItemStack ic = stacks[0].copy();
    // ic.setCount(getAmount());
    // this.stacks = new ItemStack[] { ic };
    // } else {
    // ItemStack[] itemList = new ItemStack[length];
    // for (int i = 0; i < length; i++) {
    // ItemStack ic = stacks[i].copy();
    // ic.setCount(getAmount());
    // itemList[i] = ic;
    // }
    // this.stacks = itemList;
    // }
    // this.changed = false;
    // }
    // return this.stacks;
    // }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ItemIngredient other)) {
            return false;
        }
        if (inner == other.inner) return true;
        return HASH_STRATEGY.equals(inner, other.inner);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) hashCode = HASH_STRATEGY.hashCode(inner);
        return hashCode;
    }

    public static ItemIngredient of(ItemLike itemLike) {
        return of(itemLike, 1);
    }

    public static ItemIngredient of(ItemLike itemLike, long amount) {
        var item = itemLike.asItem();
        if (item == Items.AIR) return EMPTY;
        return new ItemIngredient(ShapedRecipeBuilder.INGREDIENT_ITEM_FUNCTION.apply(item), amount);
    }

    public static ItemIngredient of(ItemStack itemStack) {
        return of(itemStack, itemStack.getCount());
    }

    public static ItemIngredient of(ItemStack itemStack, long amount) {
        var item = itemStack.getItem();
        if (item == Items.AIR) return EMPTY;
        var tag = itemStack.getTag();
        boolean hasTag = tag != null && !tag.isEmpty();
        return new ItemIngredient(hasTag ? StrictNBTIngredient.of(itemStack) : ShapedRecipeBuilder.INGREDIENT_ITEM_FUNCTION.apply(item), amount);
    }

    public static ItemIngredient of(Ingredient inner, long amount) {
        if (inner.isEmpty()) return EMPTY;
        return new ItemIngredient(inner, amount);
    }

    public static ItemIngredient of(Ingredient inner) {
        return of(inner, 1);
    }

    public static ItemIngredient of(TagKey<Item> tag) {
        return of(tag, 1);
    }

    public static ItemIngredient of(TagKey<Item> tag, long amount) {
        return new ItemIngredient(ShapedRecipeBuilder.INGREDIENT_TAG_FUNCTION.apply(tag), amount);
    }

    public static final Hash.Strategy<Ingredient> HASH_STRATEGY = new Hash.Strategy<>() {

        private static boolean valueEquals(Ingredient.Value a, Ingredient.Value b) {
            if (a instanceof Ingredient.TagValue tagValue) {
                if (!(b instanceof Ingredient.TagValue tagValue1)) {
                    return false;
                }
                return tagValue.tag == tagValue1.tag;
            } else if (a instanceof Ingredient.ItemValue itemValue) {
                if (!(b instanceof Ingredient.ItemValue itemValue1)) {
                    return false;
                }
                return itemValue.item.getItem() == itemValue1.item.getItem();
            }
            return true;
        }

        @Override
        public int hashCode(Ingredient o) {
            int hashCode = 537;
            if (o instanceof StrictNBTIngredientAccessor strict) {
                hashCode *= 31 * ItemStackHashStrategy.ITEM_AND_TAG.hashCode(strict.getStack());
            } else if (o != null) {
                for (Ingredient.Value value : o.values) {
                    if (value instanceof Ingredient.TagValue tagValue) {
                        hashCode *= 31 * tagValue.tag.hashCode();
                    } else if (value instanceof Ingredient.ItemValue itemValue) {
                        hashCode *= 31 * ItemStackHashStrategy.ITEM.hashCode(itemValue.item);
                    }
                }
            }
            return hashCode;
        }

        @Override
        public boolean equals(Ingredient a, Ingredient b) {
            if (a == b) return true;
            if (a instanceof StrictNBTIngredient strict1) {
                if (b instanceof StrictNBTIngredientAccessor strict2) {
                    return strict1.test(strict2.getStack());
                }
                return false;
            } else {
                if (a == null || b == null) return false;
                Ingredient.Value[] values1 = a.values;
                Ingredient.Value[] values2 = b.values;
                if (values1.length != values2.length) return false;
                for (int i = 0; i < values1.length; ++i) {
                    if (!valueEquals(values1[i], values2[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
    };
}
