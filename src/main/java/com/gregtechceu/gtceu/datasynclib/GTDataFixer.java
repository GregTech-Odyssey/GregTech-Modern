package com.gregtechceu.gtceu.datasynclib;

import com.gregtechceu.gtceu.api.transfer.key.StackInventory;

import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gto.datasynclib.DataSyncCodec;
import com.gto.datasynclib.blockentity.FieldDataHolderBlockEntity;
import com.gto.datasynclib.datastream.codec.ValueCodec;
import com.gto.datasynclib.datastream.codec.ValueOps;
import com.gto.datasynclib.util.ByteBufCodecExtends;
import com.gto.datasynclib.util.ValueCodecs;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

@UtilityClass
public class GTDataFixer {

    public int VERSION = 4;

    static {
        FieldDataHolderBlockEntity.VERSION = VERSION;
    }

    @Deprecated(since = "0.6.0", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
    public Object legacyTagValue(ValueOps ops, CompoundTag tag) {
        var map = new HashMap<String, Object>(tag.size());
        for (var key : tag.getAllKeys()) {
            var value = tag.get(key);
            if (value != null) map.put(key, legacyElement(ops, value));
        }
        return ops.createStringMap(map);
    }

    @Deprecated(since = "0.6.0", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
    private Object legacyElement(ValueOps ops, Tag tag) {
        return switch (tag) {
            case ByteTag t -> ops.createByte(t.getAsByte());
            case ShortTag t -> ops.createShort(t.getAsShort());
            case IntTag t -> ops.createInt(t.getAsInt());
            case LongTag t -> ops.createLong(t.getAsLong());
            case FloatTag t -> ops.createFloat(t.getAsFloat());
            case DoubleTag t -> ops.createDouble(t.getAsDouble());
            case StringTag t -> ops.createString(t.getAsString());
            case ByteArrayTag t -> ops.createByteArray(t.getAsByteArray());
            case IntArrayTag t -> ops.createIntArray(t.getAsIntArray());
            case LongArrayTag t -> ops.createLongArray(t.getAsLongArray());
            case ListTag t -> {
                var list = new ArrayList<>(t.size());
                for (var element : t) list.add(legacyElement(ops, element));
                yield ops.createList(list);
            }
            case CompoundTag t -> ValueCodecs.COMPOUND_TAG.encode(ops, t);
            default -> ops.createNull();
        };
    }

    public final ValueCodec<CompoundTag> COMPOUND_TAG = new ValueCodec<>() {

        @Override
        public Object encode(ValueOps ops, CompoundTag value) {
            return ValueCodecs.COMPOUND_TAG.encode(ops, value);
        }

        @Override
        public CompoundTag decode(ValueOps ops, Object data) {
            return ops.isCustom(data) ? ValueCodecs.COMPOUND_TAG.decode(ops, data) : legacyTag(ops, data) instanceof CompoundTag tag ? tag : new CompoundTag();
        }
    };

    public final ValueCodec<Tag> TAG = new ValueCodec<>() {

        @Override
        public Object encode(ValueOps ops, Tag value) {
            return ValueCodecs.TAG.encode(ops, value);
        }

        @Override
        public Tag decode(ValueOps ops, Object data) {
            return ops.isCustom(data) ? ValueCodecs.TAG.decode(ops, data) : legacyTag(ops, data);
        }
    };

    public final ValueCodec<ListTag> LIST_TAG = new ValueCodec<>() {

        @Override
        public Object encode(ValueOps ops, ListTag value) {
            return ValueCodecs.LIST_TAG.encode(ops, value);
        }

        @Override
        public ListTag decode(ValueOps ops, Object data) {
            return ops.isCustom(data) ? ValueCodecs.LIST_TAG.decode(ops, data) : legacyTag(ops, data) instanceof ListTag tag ? tag : new ListTag();
        }
    };

    public final ValueCodec<ItemStack> ITEM_STACK = new ValueCodec<>() {

        @Override
        public Object encode(ValueOps ops, ItemStack value) {
            return ValueCodecs.ITEM_STACK.encode(ops, value);
        }

        @Override
        public ItemStack decode(ValueOps ops, Object data) {
            return ItemStack.of(COMPOUND_TAG.decode(ops, data));
        }
    };

    public final ValueCodec<FluidStack> FLUID_STACK = new ValueCodec<>() {

        @Override
        public Object encode(ValueOps ops, FluidStack value) {
            return ValueCodecs.FLUID_STACK.encode(ops, value);
        }

        @Override
        public FluidStack decode(ValueOps ops, Object data) {
            return FluidStack.loadFluidStackFromNBT(COMPOUND_TAG.decode(ops, data));
        }
    };

    public void registerCodecs() {
        DataSyncCodec.register(Tag.class, ByteBufCodecExtends.TAG_CODEC, TAG);
        DataSyncCodec.register(CompoundTag.class, ByteBufCodecExtends.COMPOUND_TAG_CODEC, COMPOUND_TAG);
        DataSyncCodec.register(ListTag.class, ByteBufCodecExtends.LIST_TAG_CODEC, LIST_TAG);
        DataSyncCodec.register(ItemStack.class, ByteBufCodecExtends.ITEM_STACK_CODEC, ITEM_STACK);
        DataSyncCodec.register(FluidStack.class, ByteBufCodecExtends.FLUID_STACK_CODEC, FLUID_STACK);
    }

    @Deprecated(since = "0.6.0", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
    private Tag legacyTag(ValueOps ops, Object data) {
        if (ops.isNull(data)) return EndTag.INSTANCE;
        if (ops.isCustom(data)) return ValueCodecs.TAG.decode(ops, data);
        if (ops.isByte(data)) return ByteTag.valueOf(ops.getByte(data));
        if (ops.isBoolean(data)) return ByteTag.valueOf(ops.getBoolean(data));
        if (ops.isShort(data)) return ShortTag.valueOf(ops.getShort(data));
        if (ops.isChar(data)) return IntTag.valueOf(ops.getChar(data));
        if (ops.isInt(data)) return IntTag.valueOf(ops.getInt(data));
        if (ops.isLong(data)) return LongTag.valueOf(ops.getLong(data));
        if (ops.isFloat(data)) return FloatTag.valueOf(ops.getFloat(data));
        if (ops.isDouble(data)) return DoubleTag.valueOf(ops.getDouble(data));
        if (ops.isString(data)) return StringTag.valueOf(ops.getString(data));
        if (ops.isByteArray(data)) return new ByteArrayTag(ops.getByteArray(data));
        if (ops.isIntArray(data)) return new IntArrayTag(ops.getIntArray(data));
        if (ops.isLongArray(data)) return new LongArrayTag(ops.getLongArray(data));
        if (ops.isList(data)) {
            var list = new ListTag();
            for (var element : ops.getList(data)) {
                var tag = legacyTag(ops, element);
                if (tag != EndTag.INSTANCE) list.add(tag);
            }
            return list;
        }
        if (ops.isStringMap(data)) {
            var compound = new CompoundTag();
            for (var entry : ops.getStringMap(data).entrySet()) {
                var tag = legacyTag(ops, entry.getValue());
                if (tag != EndTag.INSTANCE) compound.put(entry.getKey(), tag);
            }
            return compound;
        }
        throw new IllegalArgumentException("Unsupported legacy tag value " + data);
    }

    public void decodeStackInventory(StackInventory inventory, ValueOps ops, Object data) {
        var stacks = inventory.stacks;
        Arrays.fill(stacks, ItemStack.EMPTY);
        if (ops.isNull(data)) return;
        var list = ops.getList(data);
        var size = list.size();
        var i = 0;
        if (ops.isNull(list.getFirst())) {
            inventory.isInputLimited = true;
            i++;
        }
        for (; i < size; i++) {
            var item = COMPOUND_TAG.decode(ops, list.get(i));
            var slot = item.getInt("Slot");
            if (slot >= 0 && slot < inventory.size) {
                stacks[slot] = ItemStack.of(item);
            }
        }
    }
}
