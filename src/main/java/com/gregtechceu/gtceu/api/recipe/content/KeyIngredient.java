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
import appeng.hooks.IUnique;
import com.google.gson.JsonParser;
import com.gto.datasynclib.datastream.data.Data;
import com.gto.datasynclib.datastream.data.ListData;
import com.gto.datasynclib.util.DataCodecs;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配方输入的匹配器（输出只用 EXACT/BASE）：EXACT 按驻留 key 引用、BASE 按物品/流体忽略 NBT、TAG 按 uid 集合、
 * CIRCUIT 只比编程电路配置号、PREDICATE 为原版或自定义谓词的冷路径。
 */
public final class KeyIngredient {

    public static final byte EXACT = 0, BASE = 1, TAG = 2, CIRCUIT = 3, PREDICATE = 4;

    private static final ConcurrentHashMap<Object, KeyIngredient> BASE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<AEKey, KeyIngredient> EXACT_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<TagKey<?>, KeyIngredient> TAG_CACHE = new ConcurrentHashMap<>();
    private static final KeyIngredient[] CIRCUITS = new KeyIngredient[Circuits.MAX + 1];

    public final byte kind;
    public final AEKeyType type;
    @Nullable
    private final AEKey key;
    private final int uid;
    private final int circuit;
    @Nullable
    private final TagKey<?> tag;
    @Nullable
    private final Ingredient source;
    @Nullable
    private final CompoundTag fluidNbt;
    private final int hash;
    @Nullable
    private TagMembers members;
    private int membersGeneration;
    private static volatile int tagGeneration = 1;

    private KeyIngredient(byte kind, AEKeyType type, @Nullable AEKey key, int uid, int circuit, @Nullable TagKey<?> tag, @Nullable Ingredient source, @Nullable CompoundTag fluidNbt) {
        this.kind = kind;
        this.type = type;
        this.key = key;
        this.uid = uid;
        this.circuit = circuit;
        this.tag = tag;
        this.source = source;
        this.fluidNbt = fluidNbt;
        this.hash = computeHash();
    }

    public static KeyIngredient exact(AEKey key) {
        if (key instanceof AEItemKey ik ? ik.hasTag() : key instanceof AEFluidKey fk && fk.hasTag()) return new KeyIngredient(EXACT, key.getType(), key, key.getUid(), 0, null, null, null);
        return EXACT_CACHE.computeIfAbsent(key, k -> new KeyIngredient(EXACT, k.getType(), k, k.getUid(), 0, null, null, null));
    }

    public static KeyIngredient item(ItemLike itemLike) {
        var item = itemLike.asItem();
        return BASE_CACHE.computeIfAbsent(item, i -> new KeyIngredient(BASE, AEKeyType.items(), Keys.item((Item) i), IUnique.getUid((Item) i), 0, null, null, null));
    }

    public static KeyIngredient fluid(Fluid fluid) {
        var f = Keys.source(fluid);
        return BASE_CACHE.computeIfAbsent(f, x -> new KeyIngredient(BASE, AEKeyType.fluids(), AEFluidKey.of((Fluid) x), IUnique.getUid((Fluid) x), 0, null, null, null));
    }

    public static KeyIngredient itemTag(TagKey<Item> tag) {
        return TAG_CACHE.computeIfAbsent(tag, t -> new KeyIngredient(TAG, AEKeyType.items(), null, 0, 0, t, null, null));
    }

    public static KeyIngredient fluidTag(TagKey<Fluid> tag) {
        return TAG_CACHE.computeIfAbsent(tag, t -> new KeyIngredient(TAG, AEKeyType.fluids(), null, 0, 0, t, null, null));
    }

    public static KeyIngredient fluidTag(TagKey<Fluid> tag, @Nullable CompoundTag nbt) {
        if (nbt == null || nbt.isEmpty()) return fluidTag(tag);
        return new KeyIngredient(PREDICATE, AEKeyType.fluids(), null, 0, 0, tag, null, nbt.copy());
    }

    public static KeyIngredient circuit(int configuration) {
        var c = CIRCUITS[configuration];
        if (c == null) {
            var key = Circuits.key(configuration);
            c = new KeyIngredient(CIRCUIT, AEKeyType.items(), key, key.getUid(), configuration, null, null, null);
            CIRCUITS[configuration] = c;
        }
        return c;
    }

    public static KeyIngredient of(ItemStack stack) {
        var key = Keys.itemType(stack);
        if (key == null) throw new IllegalArgumentException("Empty item ingredient");
        int config = Circuits.configOf(key);
        if (config >= 0) return circuit(config);
        var tag = stack.getTag();
        if (tag != null && !tag.isEmpty()) return new KeyIngredient(EXACT, AEKeyType.items(), key, key.getUid(), 0, null, null, null);
        return item(key.getItem());
    }

    public static KeyIngredient of(FluidStack stack) {
        var key = Keys.fluidType(stack);
        if (key == null) throw new IllegalArgumentException("Empty fluid ingredient");
        return exact(key);
    }

    public static KeyIngredient of(Fluid fluid, @Nullable CompoundTag nbt) {
        if (nbt == null || nbt.isEmpty()) return fluid(fluid);
        return exact(AEFluidKey.of(Keys.source(fluid), nbt));
    }

    public static KeyIngredient of(Ingredient ingredient) {
        if (ingredient instanceof StrictNBTIngredient strict) {
            var stacks = strict.getItems();
            if (stacks.length > 0) return of(stacks[0]);
        }
        if (ingredient.getClass() == Ingredient.class) {
            var values = ingredient.values;
            if (values.length == 1) {
                if (values[0] instanceof Ingredient.ItemValue itemValue) return item(itemValue.item.getItem());
                if (values[0] instanceof Ingredient.TagValue tagValue) return itemTag(tagValue.tag);
            } else if (values.length > 1) {
                boolean plain = true;
                for (var v : values) {
                    if (!(v instanceof Ingredient.ItemValue) && !(v instanceof Ingredient.TagValue)) {
                        plain = false;
                        break;
                    }
                }
                if (plain) return new KeyIngredient(TAG, AEKeyType.items(), null, 0, 0, null, ingredient, null);
            }
        }
        if (ingredient.isEmpty()) throw new IllegalArgumentException("Empty item ingredient");
        return new KeyIngredient(PREDICATE, AEKeyType.items(), null, 0, 0, null, ingredient, null);
    }

    public boolean isItem() {
        return type == AEKeyType.items();
    }

    public boolean isFluid() {
        return type == AEKeyType.fluids();
    }

    public int uid() {
        return uid;
    }

    public @Nullable TagKey<?> tag() {
        return tag;
    }

    public @Nullable Ingredient source() {
        return source;
    }

    public int circuitConfiguration() {
        return kind == CIRCUIT ? circuit : -1;
    }

    public boolean test(@Nullable AEKey k) {
        if (k == null) return false;
        return switch (kind) {
            case EXACT -> k == key;
            case BASE -> k.getUid() == uid && k.getType() == type;
            case TAG -> k.getType() == type && members().contains(k.getUid());
            case CIRCUIT -> k instanceof AEItemKey ik && Circuits.configOf(ik) == circuit;
            default -> testPredicate(k);
        };
    }

    public boolean test(int keyUid, AEKey k) {
        return switch (kind) {
            case EXACT -> k == key;
            case BASE -> keyUid == uid;
            case TAG -> members().contains(keyUid);
            case CIRCUIT -> keyUid == uid && Circuits.configOf((AEItemKey) k) == circuit;
            default -> testPredicate(k);
        };
    }

    public boolean test(ItemStack stack) {
        var k = Keys.itemType(stack);
        return k != null && test(k);
    }

    public boolean test(FluidStack stack) {
        var k = Keys.fluidType(stack);
        return k != null && test(k);
    }

    private boolean testPredicate(AEKey k) {
        if (k.getType() != type) return false;
        if (source != null) return k instanceof AEItemKey ik && source.test(ik.getReadOnlyStack());
        if (tag != null && k instanceof AEFluidKey fk) {
            return members().contains(fk.uid) && Objects.equals(fluidNbt, fk.getTag());
        }
        return false;
    }

    TagMembers members() {
        var m = members;
        int g = tagGeneration;
        if (m == null || membersGeneration != g) {
            m = resolveMembers();
            members = m;
            membersGeneration = g;
        }
        return m;
    }

    public static void onTagsUpdated() {
        tagGeneration++;
    }

    private TagMembers resolveMembers() {
        if (tag != null) return isItem() ? TagMembers.ofItems(tag) : TagMembers.ofFluids(tag);
        if (source != null) {
            var list = new IntArrayList();
            AEKey first = null;
            for (var stack : source.getItems()) {
                var item = stack.getItem();
                if (item == Items.AIR) continue;
                list.add(IUnique.getUid(item));
                if (first == null) first = Keys.item(item);
            }
            return TagMembers.ofUnion(list.toIntArray(), first);
        }
        return TagMembers.EMPTY;
    }

    public int[] memberUids() {
        return switch (kind) {
            case TAG -> members().uids();
            case PREDICATE -> tag != null ? members().uids() : new int[0];
            default -> new int[] { uid };
        };
    }

    public @Nullable AEKey key() {
        return key;
    }

    public @Nullable AEKey displayKey() {
        if (key != null) return key;
        if (kind == TAG || (kind == PREDICATE && tag != null)) {
            var first = members().first;
            if (first == null) return null;
            if (fluidNbt != null && first instanceof AEFluidKey fk) return AEFluidKey.of(fk.getFluid(), fluidNbt);
            return first;
        }
        if (source != null) {
            var items = source.getItems();
            return items.length == 0 ? null : Keys.item(items[0]);
        }
        return null;
    }

    public AEKey outputKey() {
        var k = displayKey();
        if (k == null) throw new IllegalStateException("Ingredient has no concrete key: " + this);
        return k;
    }

    public ItemStack[] getItems() {
        if (!isItem()) return new ItemStack[0];
        if (source != null) return source.getItems();
        if (kind == TAG) {
            var set = BuiltInRegistries.ITEM.getTag(itemTagKey());
            if (set.isEmpty()) return new ItemStack[0];
            var stacks = new ItemStack[set.get().size()];
            int i = 0;
            for (var holder : set.get()) stacks[i++] = new ItemStack(holder.value());
            return stacks;
        }
        return key == null ? new ItemStack[0] : new ItemStack[] { ((AEItemKey) key).toStack() };
    }

    public FluidStack[] getFluids(int amount) {
        if (!isFluid()) return new FluidStack[0];
        if (tag != null) {
            var set = BuiltInRegistries.FLUID.getTag(fluidTagKey());
            if (set.isEmpty()) return new FluidStack[0];
            var stacks = new FluidStack[set.get().size()];
            int i = 0;
            for (var holder : set.get()) stacks[i++] = new FluidStack(holder.value(), amount, fluidNbt);
            return stacks;
        }
        return key == null ? new FluidStack[0] : new FluidStack[] { ((AEFluidKey) key).toStack(amount) };
    }

    @SuppressWarnings("unchecked")
    public TagKey<Item> itemTagKey() {
        return (TagKey<Item>) tag;
    }

    @SuppressWarnings("unchecked")
    public TagKey<Fluid> fluidTagKey() {
        return (TagKey<Fluid>) tag;
    }

    public Component getName() {
        return switch (kind) {
            case TAG -> Component.literal((isItem() ? "Tag[" : "FluidTag[") + (tag != null ? tag.location() : "union") + "]");
            case CIRCUIT -> Component.translatable("item.gtceu.programmed_circuit").append("[" + circuit + "]");
            case PREDICATE -> Component.literal(source != null ? source.toString() : "FluidTag[" + tag.location() + "]");
            default -> key.getDisplayName();
        };
    }

    public Data toData() {
        var list = new ListData(4);
        list.addByte(kind);
        list.addBoolean(isItem());
        switch (kind) {
            case EXACT -> list.add(KeyCodecs.AE_KEY_DATA_CODEC.encode(key));
            case BASE -> list.add(DataCodecs.RESOURCE_LOCATION_CODEC.encode(key.getId()));
            case CIRCUIT -> list.addInt(circuit);
            case TAG -> {
                list.addBoolean(source != null);
                if (source != null) list.addString(source.toJson().toString());
                else list.add(DataCodecs.RESOURCE_LOCATION_CODEC.encode(tag.location()));
            }
            default -> {
                list.addBoolean(source != null);
                if (source != null) {
                    list.addString(source.toJson().toString());
                } else {
                    list.add(DataCodecs.RESOURCE_LOCATION_CODEC.encode(tag.location()));
                    list.add(DataCodecs.COMPOUND_TAG_CODEC.encode(fluidNbt));
                }
            }
        }
        return list;
    }

    public static @Nullable KeyIngredient fromData(Data data, int dataVersion) {
        var list = data.getList();
        byte kind = list.get(0).getByte();
        boolean item = list.get(1).getBoolean();
        var payload = list.get(2);
        return switch (kind) {
            case EXACT -> {
                var key = KeyCodecs.AE_KEY_DATA_CODEC.decode(payload, dataVersion);
                yield key == null ? null : exact(key);
            }
            case BASE -> {
                var id = DataCodecs.RESOURCE_LOCATION_CODEC.decode(payload, dataVersion);
                if (item) {
                    var it = BuiltInRegistries.ITEM.get(id);
                    yield it == Items.AIR ? null : item(it);
                }
                var fl = BuiltInRegistries.FLUID.get(id);
                yield fl == Fluids.EMPTY ? null : fluid(fl);
            }
            case CIRCUIT -> circuit(payload.getInt());
            case TAG -> {
                var body = list.get(3);
                if (payload.getBoolean()) yield of(Ingredient.fromJson(JsonParser.parseString(body.getString())));
                var loc = DataCodecs.RESOURCE_LOCATION_CODEC.decode(body, dataVersion);
                yield item ? itemTag(TagKey.create(Registries.ITEM, loc)) : fluidTag(TagKey.create(Registries.FLUID, loc));
            }
            default -> {
                var body = list.get(3);
                if (payload.getBoolean()) yield of(Ingredient.fromJson(JsonParser.parseString(body.getString())));
                var loc = DataCodecs.RESOURCE_LOCATION_CODEC.decode(body, dataVersion);
                yield fluidTag(TagKey.create(Registries.FLUID, loc), DataCodecs.COMPOUND_TAG_CODEC.decode(list.get(4), dataVersion));
            }
        };
    }

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeByte(kind);
        buf.writeBoolean(isItem());
        switch (kind) {
            case EXACT -> AEKey.writeKey(buf, key);
            case BASE -> buf.writeVarInt(isItem() ? BuiltInRegistries.ITEM.getId(((AEItemKey) key).getItem()) : BuiltInRegistries.FLUID.getId(((AEFluidKey) key).getFluid()));
            case CIRCUIT -> buf.writeVarInt(circuit);
            case TAG -> {
                buf.writeBoolean(tag != null);
                if (tag != null) buf.writeResourceLocation(tag.location());
                else source.toNetwork(buf);
            }
            default -> {
                buf.writeBoolean(source != null);
                if (source != null) {
                    source.toNetwork(buf);
                } else {
                    buf.writeResourceLocation(tag.location());
                    buf.writeNbt(fluidNbt);
                }
            }
        }
    }

    public static KeyIngredient fromNetwork(FriendlyByteBuf buf) {
        byte kind = buf.readByte();
        boolean item = buf.readBoolean();
        return switch (kind) {
            case EXACT -> exact(AEKey.readKey(buf));
            case BASE -> item ? item(BuiltInRegistries.ITEM.byId(buf.readVarInt())) : fluid(BuiltInRegistries.FLUID.byId(buf.readVarInt()));
            case CIRCUIT -> circuit(buf.readVarInt());
            case TAG -> {
                if (!buf.readBoolean()) yield of(Ingredient.fromNetwork(buf));
                ResourceLocation loc = buf.readResourceLocation();
                yield item ? itemTag(TagKey.create(Registries.ITEM, loc)) : fluidTag(TagKey.create(Registries.FLUID, loc));
            }
            default -> {
                if (buf.readBoolean()) yield of(Ingredient.fromNetwork(buf));
                ResourceLocation loc = buf.readResourceLocation();
                yield fluidTag(TagKey.create(Registries.FLUID, loc), buf.readNbt());
            }
        };
    }

    private int computeHash() {
        int h = kind * 31 + (isItem() ? 1 : 2);
        if (key != null) h = h * 31 + System.identityHashCode(key);
        if (tag != null) h = h * 31 + tag.hashCode();
        if (source != null) h = h * 31 + System.identityHashCode(source);
        if (fluidNbt != null) h = h * 31 + fluidNbt.hashCode();
        return h * 31 + circuit;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof KeyIngredient o) || o.hash != hash || o.kind != kind || o.type != type) return false;
        return o.key == key && o.circuit == circuit && Objects.equals(o.tag, tag) && o.source == source && Objects.equals(o.fluidNbt, fluidNbt);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case EXACT -> "Exact[" + key + "]";
            case BASE -> "Base[" + key + "]";
            case CIRCUIT -> "Circuit[" + circuit + "]";
            case TAG -> "Tag[" + (tag != null ? tag.location() : "union") + "]";
            default -> "Predicate[" + (source != null ? source : tag.location()) + "]";
        };
    }
}
