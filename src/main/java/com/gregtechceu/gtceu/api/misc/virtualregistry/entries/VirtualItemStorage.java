package com.gregtechceu.gtceu.api.misc.virtualregistry.entries;

import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.datasynclib.GTDataFixer;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.datastream.codec.JavaValueOps;
import com.gto.datasynclib.datastream.codec.ValueOps;
import com.gto.datasynclib.datastream.codec.ValueCodec;
import com.gto.datasynclib.util.ValueCodecs;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class VirtualItemStorage extends VirtualEntry {

    public static final ValueCodec<VirtualItemStorage> DATA_CODEC = new ValueCodec<>() {

        @Override
        public VirtualItemStorage decode(ValueOps ops, Object data) {
            var tank = new VirtualItemStorage();
            tank.deserializeNBT(ValueCodecs.COMPOUND_TAG.decode(ops, data));
            return tank;
        }

        @Override
        public Object encode(ValueOps ops, VirtualItemStorage obj) {
            return ValueCodecs.COMPOUND_TAG.encode(ops, obj.serializeNBT());
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
        tag.put(ITEM_KEY, new ByteArrayTag(JavaValueOps.INSTANCE.toBytes(handler.writeValue(JavaValueOps.INSTANCE))));
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        super.deserializeNBT(nbt);
        var items = nbt.get(ITEM_KEY);
        if (items instanceof ByteArrayTag bytes) {
            handler.readValue(JavaValueOps.INSTANCE.fromBytes(bytes.getAsByteArray()), JavaValueOps.create(GTDataFixer.VERSION));
        } else if (items instanceof CompoundTag legacy) {
            handler.clear();
            var list = legacy.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                var itemTag = list.getCompound(i);
                int slot = itemTag.getInt("Slot");
                var stack = ItemStack.of(itemTag);
                if (slot >= 0 && slot < handler.size() && !stack.isEmpty()) handler.set(slot, AEItemKey.of(stack), stack.getCount());
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
