package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.transfer.key.KeyCodecs;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import com.google.gson.JsonParser;
import com.gto.datasynclib.datastream.codec.ValueOps;
import com.gto.datasynclib.util.ValueCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 配方内容的匹配器：BASE/EXACT 由 AE 规范 key 自身充当（无 NBT 按 uid、带 NBT 按身份），其余种类各为只含 final 字段的小实现类。
 * 热路径只走静态的 {@link #accepts}/{@link #acceptsStack}，按 instanceof 静态分派。
 */
public interface KeyIngredient {

    byte EXACT = 0, BASE = 1, TAG = 2, CIRCUIT = 3, PREDICATE = 4;

    byte kind();

    AEKeyType getType();

    @Nullable
    AEKey key();

    boolean test(AEKey key);

    default int uid() {
        var k = key();
        return k == null ? 0 : k.getUid();
    }

    default @Nullable TagKey<?> tagKey() {
        return null;
    }

    default @Nullable Ingredient source() {
        return null;
    }

    default int circuitConfiguration() {
        return -1;
    }

    default boolean isItem() {
        return getType() == AEKeyTypes.ITEMS;
    }

    default boolean isFluid() {
        return getType() == AEKeyTypes.FLUIDS;
    }

    default boolean test(ItemStack stack) {
        return acceptsStack(this, stack);
    }

    default boolean test(FluidStack stack) {
        var k = Keys.fluidType(stack);
        return k != null && test(k);
    }

    default @Nullable AEKey displayKey() {
        return key();
    }

    default AEKey outputKey() {
        var k = displayKey();
        if (k == null) throw new IllegalStateException("Ingredient has no concrete key: " + this);
        return k;
    }

    default ItemStack[] getItems() {
        return key() instanceof AEItemKey k ? new ItemStack[] { k.toStack() } : new ItemStack[0];
    }

    default FluidStack[] getFluids(int amount) {
        return key() instanceof AEFluidKey k ? new FluidStack[] { k.toStack(amount) } : new FluidStack[0];
    }

    @SuppressWarnings("unchecked")
    default TagKey<Item> itemTagKey() {
        return (TagKey<Item>) tagKey();
    }

    @SuppressWarnings("unchecked")
    default TagKey<Fluid> fluidTagKey() {
        return (TagKey<Fluid>) tagKey();
    }

    default Component getName() {
        return key().getDisplayName();
    }

    default Object toData(ValueOps ops) {
        byte kind = kind();
        var key = key();
        return ops.createList(
                ops.createByte(kind),
                ops.createBoolean(isItem()),
                kind == EXACT ? KeyCodecs.AE_KEY_DATA_CODEC.encode(ops, key)
                        : ValueCodecs.RESOURCE_LOCATION.encode(ops, key.getId()));
    }

    default void toNetwork(FriendlyByteBuf buf) {
        byte kind = kind();
        var key = key();
        buf.writeByte(kind);
        if (key instanceof AEItemKey ik) {
            buf.writeBoolean(true);
            if (kind == EXACT) AEKey.writeKey(buf, ik);
            else buf.writeVarInt(BuiltInRegistries.ITEM.getId(ik.item));
        } else {
            buf.writeBoolean(false);
            if (kind == EXACT) AEKey.writeKey(buf, key);
            else buf.writeVarInt(BuiltInRegistries.FLUID.getId(((AEFluidKey) key).fluid));
        }
    }

    static KeyIngredient exact(AEKey key) {
        return (KeyIngredient) key;
    }

    static KeyIngredient item(ItemLike itemLike) {
        return exact(AEItemKey.of(itemLike, null));
    }

    static KeyIngredient fluid(Fluid fluid) {
        return exact(AEFluidKey.of(Keys.source(fluid)));
    }

    static KeyIngredient itemTag(TagKey<Item> tag) {
        return ItemTagIngredient.of(tag);
    }

    static KeyIngredient fluidTag(TagKey<Fluid> tag) {
        return FluidTagIngredient.of(tag);
    }

    static KeyIngredient fluidTag(TagKey<Fluid> tag, @Nullable CompoundTag nbt) {
        if (nbt == null || nbt.isEmpty()) return FluidTagIngredient.of(tag);
        return new FluidTagNbtIngredient(tag, nbt.copy());
    }

    static KeyIngredient circuit(int configuration) {
        return CircuitIngredient.of(configuration);
    }

    static KeyIngredient of(ItemStack stack) {
        var key = AEItemKey.of(stack);
        if (key == null) throw new IllegalArgumentException("Empty item ingredient");
        int config = Circuits.configOf(key);
        if (config >= 0) return CircuitIngredient.of(config);
        return key.hasTag() ? exact(key) : item(key.item);
    }

    static KeyIngredient of(FluidStack stack) {
        var key = Keys.fluidType(stack);
        if (key == null) throw new IllegalArgumentException("Empty fluid ingredient");
        return exact(key);
    }

    static KeyIngredient of(Fluid fluid, @Nullable CompoundTag nbt) {
        if (nbt == null || nbt.isEmpty()) return fluid(fluid);
        return exact(AEFluidKey.of(Keys.source(fluid), nbt));
    }

    static KeyIngredient of(Ingredient ingredient) {
        if (ingredient instanceof StrictNBTIngredient strict) {
            var stacks = strict.getItems();
            if (stacks.length > 0) return of(stacks[0]);
        }
        if (ingredient.getClass() == Ingredient.class) {
            var values = ingredient.values;
            if (values.length == 1) {
                if (values[0] instanceof Ingredient.ItemValue itemValue) return item(itemValue.item.getItem());
                if (values[0] instanceof Ingredient.TagValue tagValue) return ItemTagIngredient.of(tagValue.tag);
            } else if (values.length > 1) {
                boolean plain = true;
                for (var v : values) {
                    if (!(v instanceof Ingredient.ItemValue) && !(v instanceof Ingredient.TagValue)) {
                        plain = false;
                        break;
                    }
                }
                if (plain) return new UnionIngredient(ingredient);
            }
        }
        if (ingredient.isEmpty()) throw new IllegalArgumentException("Empty item ingredient");
        return new PredicateIngredient(ingredient);
    }

    static boolean accepts(KeyIngredient ing, int uid, AEKey k) {
        Object o = ing;
        if (o instanceof AEItemKey ik) return ik.hasTag() ? k == ik : uid == ik.uid;
        if (o instanceof AEFluidKey fk) return fk.hasTag() ? k == fk : uid == fk.uid;
        if (o instanceof ItemTagIngredient t) return k instanceof AEItemKey ik && ik.item.builtInRegistryHolder().is(t.tag);
        if (o instanceof FluidTagIngredient t) return k instanceof AEFluidKey fk && fk.fluid.is(t.tag);
        if (o instanceof CircuitIngredient c) return uid == Circuits.uid() && Circuits.key(c.config) == k;
        return ing.test(k);
    }

    static boolean acceptsStack(KeyIngredient ing, ItemStack stack) {
        Object o = ing;
        if (o instanceof AEItemKey ik) return ik.hasTag() ? ik.matches(stack) : stack.getItem() == ik.item;
        if (o instanceof ItemTagIngredient t) return stack.is(t.tag);
        if (o instanceof CircuitIngredient c) return Circuits.configOf(stack) == c.config;
        if (o instanceof UnionIngredient u) return u.matches(stack.getItem());
        if (o instanceof PredicateIngredient p) return p.ingredient.test(stack);
        var k = AEItemKey.of(stack);
        return k != null && ing.test(k);
    }

    static @Nullable KeyIngredient fromData(Object data, ValueOps ops) {
        var list = ops.getList(data);
        byte kind = ops.getByte(list, 0);
        boolean item = ops.getBoolean(list, 1);
        var payload = list.get(2);
        return switch (kind) {
            case EXACT -> KeyCodecs.AE_KEY_DATA_CODEC.decode(ops, payload) instanceof KeyIngredient ing ? ing : null;
            case BASE -> {
                var id = ValueCodecs.RESOURCE_LOCATION.decode(ops, payload);
                if (item) {
                    var it = BuiltInRegistries.ITEM.get(id);
                    yield it == Items.AIR ? null : item(it);
                }
                var fl = BuiltInRegistries.FLUID.get(id);
                yield fl == Fluids.EMPTY ? null : fluid(fl);
            }
            case CIRCUIT -> CircuitIngredient.of(ops.getInt(payload));
            case TAG -> {
                var body = list.get(3);
                if (ops.getBoolean(payload)) yield of(Ingredient.fromJson(ValueCodecs.JSON.decode(ops, body)));
                var loc = ValueCodecs.RESOURCE_LOCATION.decode(ops, body);
                yield item ? ItemTagIngredient.of(TagKey.create(Registries.ITEM, loc)) : FluidTagIngredient.of(TagKey.create(Registries.FLUID, loc));
            }
            default -> {
                var body = list.get(3);
                if (ops.getBoolean(payload)) yield of(Ingredient.fromJson(ValueCodecs.JSON.decode(ops, body)));
                var loc = ValueCodecs.RESOURCE_LOCATION.decode(ops, body);
                yield fluidTag(TagKey.create(Registries.FLUID, loc), ValueCodecs.COMPOUND_TAG.decode(ops, list.get(4)));
            }
        };
    }

    static KeyIngredient fromNetwork(FriendlyByteBuf buf) {
        byte kind = buf.readByte();
        boolean item = buf.readBoolean();
        return switch (kind) {
            case EXACT -> exact(AEKey.readKey(buf));
            case BASE -> item ? item(BuiltInRegistries.ITEM.byId(buf.readVarInt())) : fluid(BuiltInRegistries.FLUID.byId(buf.readVarInt()));
            case CIRCUIT -> CircuitIngredient.of(buf.readVarInt());
            case TAG -> {
                if (!buf.readBoolean()) yield of(Ingredient.fromNetwork(buf));
                ResourceLocation loc = buf.readResourceLocation();
                yield item ? ItemTagIngredient.of(TagKey.create(Registries.ITEM, loc)) : FluidTagIngredient.of(TagKey.create(Registries.FLUID, loc));
            }
            default -> {
                if (buf.readBoolean()) yield of(Ingredient.fromNetwork(buf));
                ResourceLocation loc = buf.readResourceLocation();
                yield fluidTag(TagKey.create(Registries.FLUID, loc), buf.readNbt());
            }
        };
    }
}
