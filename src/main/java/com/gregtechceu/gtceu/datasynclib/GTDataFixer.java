package com.gregtechceu.gtceu.datasynclib;

import com.gregtechceu.gtceu.api.transfer.key.StackInventory;

import net.minecraft.world.item.ItemStack;

import com.gto.datasynclib.blockentity.FieldDataHolderBlockEntity;
import com.gto.datasynclib.datastream.codec.ValueOps;
import com.gto.datasynclib.util.ValueCodecs;
import lombok.experimental.UtilityClass;

import java.util.Arrays;

@UtilityClass
public class GTDataFixer {

    public int VERSION = 4;

    static {
        FieldDataHolderBlockEntity.VERSION = VERSION;
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
