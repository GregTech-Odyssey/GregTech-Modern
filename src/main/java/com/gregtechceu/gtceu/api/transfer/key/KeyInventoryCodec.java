package com.gregtechceu.gtceu.api.transfer.key;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.gto.datasynclib.util.NbtUtil;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * KeyInventory 存档紧凑格式：int 头，varint 条目数，每条为 varint 槽号间隔、varint 键引用（小于表长为复用，等于表长为新键无 tag，表长+1 为新键带 tag）、新键内容、varLong 数量。
 * 物品/流体新键写注册名字符串与可选 CompoundTag，其它类型写 toTag()。
 */
final class KeyInventoryCodec {

    private static final int LINEAR_LOOKUP = 32;

    private KeyInventoryCodec() {}

    static byte[] encode(KeyInventory<?> inv, int header, int count) {
        var type = inv.keyType();
        boolean items = type == AEKeyType.items();
        boolean fluids = type == AEKeyType.fluids();
        boolean unique = inv.isUniqueKeys();
        var raw = Unpooled.buffer(8 + count * 8);
        try {
            var buf = new FriendlyByteBuf(raw);
            buf.writeInt(header);
            buf.writeVarInt(count);
            AEKey[] table = unique ? null : new AEKey[Math.min(count, LINEAR_LOOKUP)];
            Reference2IntOpenHashMap<AEKey> map = null;
            int tableSize = 0;
            int prev = -1;
            int size = inv.size();
            for (int i = 0; i < size; i++) {
                long amount = inv.amountAt(i);
                if (amount <= 0) continue;
                var key = inv.rawKeyAt(i);
                if (key.getType() != type) continue;
                buf.writeVarInt(i - prev - 1);
                prev = i;
                int ref = -1;
                if (!unique) {
                    if (map != null) {
                        ref = map.getInt(key);
                    } else {
                        for (int t = 0; t < tableSize; t++) {
                            if (table[t] == key) {
                                ref = t;
                                break;
                            }
                        }
                    }
                }
                if (ref >= 0) {
                    buf.writeVarInt(ref);
                } else {
                    writeNewKey(buf, key, tableSize, items, fluids);
                    if (!unique) {
                        if (map != null) {
                            map.put(key, tableSize);
                        } else if (tableSize < table.length) {
                            table[tableSize] = key;
                        } else {
                            map = new Reference2IntOpenHashMap<>(count);
                            map.defaultReturnValue(-1);
                            for (int t = 0; t < tableSize; t++) map.put(table[t], t);
                            map.put(key, tableSize);
                        }
                    }
                    tableSize++;
                }
                buf.writeVarLong(amount);
            }
            byte[] out = new byte[raw.writerIndex()];
            raw.getBytes(0, out);
            return out;
        } finally {
            raw.release();
        }
    }

    private static void writeNewKey(FriendlyByteBuf buf, AEKey key, int tableSize, boolean items, boolean fluids) {
        CompoundTag tag;
        String id;
        if (items) {
            var k = (AEItemKey) key;
            tag = k.getTag();
            id = BuiltInRegistries.ITEM.getKey(k.getItem()).toString();
        } else if (fluids) {
            var k = (AEFluidKey) key;
            tag = k.getTag();
            id = BuiltInRegistries.FLUID.getKey(k.getFluid()).toString();
        } else {
            buf.writeVarInt(tableSize);
            NbtUtil.write(key.toTag(), buf);
            return;
        }
        buf.writeVarInt(tag == null ? tableSize : tableSize + 1);
        buf.writeUtf(id);
        if (tag != null) NbtUtil.write(tag, buf);
    }

    static void decode(KeyInventory<?> inv, byte[] bytes) {
        var type = inv.keyType();
        boolean items = type == AEKeyType.items();
        boolean fluids = type == AEKeyType.fluids();
        var buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            buf.skipBytes(4);
            int count = buf.readVarInt();
            if (count <= 0) return;
            var table = new AEKey[Math.min(count, LINEAR_LOOKUP)];
            int tableSize = 0;
            int slot = -1;
            for (int n = 0; n < count; n++) {
                slot += buf.readVarInt() + 1;
                if (slot < 0) return;
                int ref = buf.readVarInt();
                AEKey key;
                if (ref >= 0 && ref < tableSize) {
                    key = table[ref];
                } else if (ref == tableSize || ref == tableSize + 1) {
                    key = readNewKey(buf, type, items, fluids, ref != tableSize);
                    if (tableSize == table.length) table = Arrays.copyOf(table, tableSize << 1);
                    table[tableSize++] = key;
                } else {
                    return;
                }
                inv.put(slot, key, buf.readVarLong());
            }
        } finally {
            buf.release();
        }
    }

    private static @Nullable AEKey readNewKey(FriendlyByteBuf buf, AEKeyType type, boolean items, boolean fluids, boolean hasTag) {
        if (!items && !fluids) {
            var tag = (CompoundTag) NbtUtil.read(Tag.TAG_COMPOUND, buf);
            try {
                return type.loadKeyFromTag(tag);
            } catch (RuntimeException e) {
                return null;
            }
        }
        var id = ResourceLocation.tryParse(buf.readUtf());
        var tag = hasTag ? (CompoundTag) NbtUtil.read(Tag.TAG_COMPOUND, buf) : null;
        if (id == null) return null;
        if (items) {
            var item = BuiltInRegistries.ITEM.get(id);
            if (item == Items.AIR) return null;
            return tag == null ? AEItemKey.of(item) : AEItemKey.of(item, tag);
        }
        var fluid = BuiltInRegistries.FLUID.get(id);
        if (fluid == Fluids.EMPTY) return null;
        return tag == null ? AEFluidKey.of(fluid) : AEFluidKey.of(fluid, tag);
    }
}
