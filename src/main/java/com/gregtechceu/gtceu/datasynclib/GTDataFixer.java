package com.gregtechceu.gtceu.datasynclib;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.world.item.ItemStack;

import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.MapData;
import com.gto.datasynclib.datasream.data.NullData;
import com.gto.datasynclib.util.DataCodecs;
import lombok.experimental.UtilityClass;

import java.util.Arrays;

@UtilityClass
public class GTDataFixer {

    public int VERSION = 1;

    public CoverBehavior decodeCover(ICoverable coverable, Data data, int dataVersion) {
        if (dataVersion == -1) {
            var map = data.getMap();
            var definitionId = GTUtil.getResourceLocation(map.get("id").getString());
            var definition = GTRegistries.COVERS.get(definitionId);
            if (definition != null) {
                return definition.createCoverBehavior(coverable, GTUtil.DIRECTIONS[map.get("side").getInt()]);
            }
        } else {
            var list = data.getList();
            var definition = GTRegistries.COVERS.dataCodec().decode(list.getFirst(), dataVersion);
            if (definition != null) {
                return definition.createCoverBehavior(coverable, GTUtil.DIRECTIONS[list.get(1).getByte()]);
            }
        }
        GTCEu.LOGGER.error("couldn't find cover definition {}", data);
        throw new RuntimeException();
    }

    public void decodeCustomItemStackHandler(CustomItemStackHandler inventory, Data data, int dataVersion) {
        var stacks = inventory.stacks;
        Arrays.fill(stacks, ItemStack.EMPTY);
        if (data == NullData.INSTANCE) return;
        if (dataVersion < 1 && data instanceof MapData mapData) {
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
