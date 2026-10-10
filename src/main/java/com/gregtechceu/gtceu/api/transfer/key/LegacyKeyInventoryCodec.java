package com.gregtechceu.gtceu.api.transfer.key;

import com.gregtechceu.gtceu.api.recipe.content.Circuits;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import com.gto.datasynclib.datastream.codec.ValueOps;
import com.gto.datasynclib.util.ValueCodecs;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

@Deprecated(since = "0.6.0", forRemoval = true)
@ApiStatus.ScheduledForRemoval(inVersion = "0.7.0")
final class LegacyKeyInventoryCodec {

    private LegacyKeyInventoryCodec() {}

    static void decode(KeyInventory<?> inv, Object data, ValueOps ops) {
        if (ops.isNull(data)) return;
        if (inv.keyType() == AEKeyTypes.ITEMS) {
            decodeItems(inv, data, ops);
        } else {
            decodeFluids(inv, data, ops);
        }
    }

    private static void decodeItems(KeyInventory<?> inv, Object data, ValueOps ops) {
        if (ops.isByte(data)) {
            byte config = ops.getByte(data);
            if (config >= 0) inv.put(0, Circuits.key(config), 1);
            return;
        }
        if (ops.isCustom(data)) {
            putItem(inv, 0, ValueCodecs.COMPOUND_TAG.decode(ops, data));
            return;
        }
        if (!ops.isList(data)) return;
        var list = ops.getList(data);
        if (list.isEmpty()) return;
        if (isKeyMap(ops, list)) {
            decodeKeyMap(inv, list, ops);
            return;
        }
        int i = 0;
        if (ops.isNull(list.get(0))) {
            inv.legacyUniqueKeys(true);
            i++;
        }
        for (; i < list.size(); i++) {
            var tag = ValueCodecs.COMPOUND_TAG.decode(ops, list.get(i));
            putItem(inv, tag.getInt("Slot"), tag);
        }
    }

    private static void putItem(KeyInventory<?> inv, int slot, CompoundTag tag) {
        var stack = ItemStack.of(tag);
        if (!stack.isEmpty()) inv.put(slot, AEItemKey.of(stack), stack.getCount());
    }

    private static boolean isKeyMap(ValueOps ops, List<Object> list) {
        return list.size() >= 2 && ops.isLong(list.get(1));
    }

    private static void decodeKeyMap(KeyInventory<?> inv, List<Object> list, ValueOps ops) {
        int size = list.size();
        int slot = 0;
        boolean items = inv.keyType() == AEKeyTypes.ITEMS;
        for (int i = 0; i + 1 < size; i += 2) {
            AEKey key = items ? KeyCodecs.AE_ITEM_KEY_DATA_CODEC.decode(ops, list.get(i)) : KeyCodecs.AE_FLUID_KEY_DATA_CODEC.decode(ops, list.get(i));
            long amount = ops.getLong(list, i + 1);
            if (key != null && amount > 0) inv.put(slot++, key, amount);
        }
    }

    private static void decodeFluids(KeyInventory<?> inv, Object data, ValueOps ops) {
        if (ops.isList(data)) {
            var list = ops.getList(data);
            if (isKeyMap(ops, list)) {
                decodeKeyMap(inv, list, ops);
                return;
            }
            for (int i = 0; i < list.size(); i++) {
                var element = list.get(i);
                if (ops.isNull(element)) continue;
                putFluid(inv, i, ValueCodecs.COMPOUND_TAG.decode(ops, element));
            }
        } else {
            putFluid(inv, 0, ValueCodecs.COMPOUND_TAG.decode(ops, data));
        }
    }

    private static void putFluid(KeyInventory<?> inv, int slot, CompoundTag tag) {
        var stack = FluidStack.loadFluidStackFromNBT(tag);
        if (!stack.isEmpty()) inv.put(slot, Keys.fluid(stack), stack.getAmount());
    }
}
