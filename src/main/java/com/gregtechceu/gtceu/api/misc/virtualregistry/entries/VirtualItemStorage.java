package com.gregtechceu.gtceu.api.misc.virtualregistry.entries;

import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.datasynclib.GTDataFixer;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.datastream.codec.DataCodec;
import com.gto.datasynclib.datastream.data.Data;
import com.gto.datasynclib.util.DataCodecs;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class VirtualItemStorage extends VirtualEntry {

    public static final DataCodec<VirtualItemStorage> DATA_CODEC = new DataCodec<>() {

        @Override
        public VirtualItemStorage decode(Data data, int dataVersion) {
            var tank = new VirtualItemStorage();
            tank.deserializeNBT(DataCodecs.COMPOUND_TAG_CODEC.decode(data, dataVersion));
            return tank;
        }

        @Override
        public Data encode(VirtualItemStorage obj) {
            return DataCodecs.COMPOUND_TAG_CODEC.encode(obj.serializeNBT());
        }
    };

    protected static final int DEFAULT_SLOT_AMOUNT = 1;

    @NotNull
    @Getter
    private final KeyInventory<AEItemKey> handler;

    protected static final String ITEM_KEY = "items";

    public VirtualItemStorage() {
        this(DEFAULT_SLOT_AMOUNT);
    }

    public VirtualItemStorage(int slots) {
        handler = KeyInventory.items(slots);
    }

    @Override
    public EntryTypes<? extends VirtualEntry> getType() {
        return EntryTypes.ENDER_ITEM;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VirtualItemStorage other)) return false;
        return other.handler == this.handler;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        tag.put(ITEM_KEY, new ByteArrayTag(handler.writeData().writeToBytes()));
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        super.deserializeNBT(nbt);
        var items = nbt.get(ITEM_KEY);
        if (items instanceof ByteArrayTag bytes) {
            handler.readData(Data.readData(bytes.getAsByteArray()), GTDataFixer.VERSION);
        } else if (items instanceof CompoundTag legacy) {
            handler.clear();
            var list = legacy.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                var itemTag = list.getCompound(i);
                int slot = itemTag.getInt("Slot");
                var stack = ItemStack.of(itemTag);
                if (slot >= 0 && slot < handler.size() && !stack.isEmpty()) handler.set(slot, Keys.item(stack), stack.getCount());
            }
        }
    }

    @Override
    public boolean canRemove() {
        return super.canRemove() && isEmpty();
    }

    public boolean isEmpty() {
        return handler.isEmpty();
    }
}
