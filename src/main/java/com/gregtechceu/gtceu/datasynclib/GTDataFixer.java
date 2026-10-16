package com.gregtechceu.gtceu.datasynclib;

import com.gregtechceu.gtceu.api.transfer.key.StackInventory;

import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

import com.gto.datasynclib.blockentity.FieldDataHolderBlockEntity;
import com.gto.datasynclib.datastream.codec.ValueOps;
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
            var item = ValueCodecs.COMPOUND_TAG.decode(ops, list.get(i));
            var slot = item.getInt("Slot");
            if (slot >= 0 && slot < inventory.size) {
                stacks[slot] = ItemStack.of(item);
            }
        }
    }
}
