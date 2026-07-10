package com.gregtechceu.gtceu.datasynclib;

import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import net.minecraft.world.item.ItemStack;

import com.gto.datasynclib.blockentity.FieldDataHolderBlockEntity;
import com.gto.datasynclib.datastream.data.Data;
import com.gto.datasynclib.datastream.data.NullData;
import com.gto.datasynclib.datastream.data.StringMapData;
import com.gto.datasynclib.util.DataCodecs;
import lombok.experimental.UtilityClass;

import java.util.Arrays;

@UtilityClass
public class GTDataFixer {

    public int VERSION = 1;

    static {
        FieldDataHolderBlockEntity.VERSION = VERSION;
    }

    public void decodeCustomItemStackHandler(CustomItemStackHandler inventory, Data data, int dataVersion) {
        var stacks = inventory.stacks;
        Arrays.fill(stacks, ItemStack.EMPTY);
        if (data == NullData.INSTANCE) return;
        if (dataVersion < 1 && data instanceof StringMapData mapData) {
            var list = mapData.getList("Items");
            for (int i = 0; i < list.size(); i++) {
                var items = list.getMap(i);
                int slot = items.getInt("Slot");
                if (slot >= 0 && slot < inventory.size) {
                    stacks[slot] = ItemStack.of(DataCodecs.COMPOUND_TAG_CODEC.decode(items, VERSION));
                }
            }
            inventory.isInputLimited = mapData.getBoolean("il");
        } else {
            var list = data.getList();
            var size = list.size();
            var i = 0;
            if (list.getFirst() == NullData.INSTANCE) {
                inventory.isInputLimited = true;
                i++;
            }
            for (; i < size; i++) {
                var item = DataCodecs.COMPOUND_TAG_CODEC.decode(list.get(i));
                var slot = item.getInt("Slot");
                if (slot >= 0 && slot < inventory.size) {
                    stacks[slot] = ItemStack.of(item);
                }
            }
        }
    }
}
