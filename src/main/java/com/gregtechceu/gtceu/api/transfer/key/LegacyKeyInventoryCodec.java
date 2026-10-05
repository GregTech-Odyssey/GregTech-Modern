package com.gregtechceu.gtceu.api.transfer.key;

import com.gregtechceu.gtceu.api.recipe.content.Circuits;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import com.gto.datasynclib.datastream.data.ByteData;
import com.gto.datasynclib.datastream.data.CustomData;
import com.gto.datasynclib.datastream.data.Data;
import com.gto.datasynclib.datastream.data.ListData;
import com.gto.datasynclib.datastream.data.LongData;
import com.gto.datasynclib.datastream.data.NullData;
import com.gto.datasynclib.datastream.data.StringMapData;
import com.gto.datasynclib.util.DataCodecs;
import org.jetbrains.annotations.ApiStatus;

@Deprecated(since = "0.6.0", forRemoval = true)
@ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
final class LegacyKeyInventoryCodec {

    private LegacyKeyInventoryCodec() {}

    static void decode(KeyInventory<?> inv, Data data, int dataVersion) {
        if (data == NullData.INSTANCE || data == NullData.NONE) return;
        if (inv.keyType() == AEKeyTypes.ITEMS) {
            decodeItems(inv, data, dataVersion);
        } else {
            decodeFluids(inv, data, dataVersion);
        }
    }

    private static void decodeItems(KeyInventory<?> inv, Data data, int dataVersion) {
        if (dataVersion < 1 && data instanceof StringMapData map) {
            var list = map.getList("Items");
            for (int i = 0; i < list.size(); i++) {
                var item = list.getMap(i);
                putItem(inv, item.getInt("Slot"), DataCodecs.COMPOUND_TAG_CODEC.decode(item, dataVersion));
            }
            inv.legacyUniqueKeys(map.getBoolean("il"));
            return;
        }
        if (data instanceof ByteData(byte config)) {
            if (config >= 0) inv.put(0, Circuits.key(config), 1);
            return;
        }
        if (data instanceof StringMapData || data instanceof CustomData<?>) {
            putItem(inv, 0, DataCodecs.COMPOUND_TAG_CODEC.decode(data, dataVersion));
            return;
        }
        if (!(data instanceof ListData list) || list.isEmpty()) return;
        if (isKeyMap(list)) {
            decodeKeyMap(inv, list, dataVersion);
            return;
        }
        int i = 0;
        if (list.get(0) == NullData.INSTANCE) {
            inv.legacyUniqueKeys(true);
            i++;
        }
        for (; i < list.size(); i++) {
            var tag = DataCodecs.COMPOUND_TAG_CODEC.decode(list.get(i), dataVersion);
            putItem(inv, tag.getInt("Slot"), tag);
        }
    }

    private static void putItem(KeyInventory<?> inv, int slot, CompoundTag tag) {
        var stack = ItemStack.of(tag);
        if (!stack.isEmpty()) inv.put(slot, AEItemKey.of(stack), stack.getCount());
    }

    private static boolean isKeyMap(ListData list) {
        return list.size() >= 2 && list.get(1) instanceof LongData;
    }

    private static void decodeKeyMap(KeyInventory<?> inv, ListData list, int dataVersion) {
        int size = list.size();
        int slot = 0;
        boolean items = inv.keyType() == AEKeyTypes.ITEMS;
        for (int i = 0; i + 1 < size; i += 2) {
            AEKey key = items ? KeyCodecs.AE_ITEM_KEY_DATA_CODEC.decode(list.get(i), dataVersion) : KeyCodecs.AE_FLUID_KEY_DATA_CODEC.decode(list.get(i), dataVersion);
            long amount = list.get(i + 1).getLong();
            if (key != null && amount > 0) inv.put(slot++, key, amount);
        }
    }

    private static void decodeFluids(KeyInventory<?> inv, Data data, int dataVersion) {
        if (data instanceof ListData list) {
            if (isKeyMap(list)) {
                decodeKeyMap(inv, list, dataVersion);
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                var element = list.get(i);
                if (dataVersion == -1 && element instanceof StringMapData map) element = map.get("p");
                if (element == null || element == NullData.INSTANCE) continue;
                putFluid(inv, i, DataCodecs.COMPOUND_TAG_CODEC.decode(element, dataVersion));
            }
        } else {
            putFluid(inv, 0, DataCodecs.COMPOUND_TAG_CODEC.decode(data, dataVersion));
        }
    }

    private static void putFluid(KeyInventory<?> inv, int slot, CompoundTag tag) {
        var stack = FluidStack.loadFluidStackFromNBT(tag);
        if (!stack.isEmpty()) inv.put(slot, Keys.fluid(stack), stack.getAmount());
    }
}
