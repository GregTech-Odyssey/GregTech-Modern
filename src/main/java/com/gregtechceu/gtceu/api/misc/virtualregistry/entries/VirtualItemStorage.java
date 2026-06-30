package com.gregtechceu.gtceu.api.misc.virtualregistry.entries;

import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;

import com.gto.datasynclib.datasream.codec.DataCodec;
import com.gto.datasynclib.datasream.data.Data;
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
    private final CustomItemStackHandler handler;

    protected static final String ITEM_KEY = "items";

    public VirtualItemStorage() {
        this(DEFAULT_SLOT_AMOUNT);
    }

    public VirtualItemStorage(int slots) {
        handler = new CustomItemStackHandler(slots);
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
        tag.put(ITEM_KEY, handler.serializeNBT());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        super.deserializeNBT(nbt);
        handler.deserializeNBT(nbt.get(ITEM_KEY));
    }

    @Override
    public boolean canRemove() {
        return super.canRemove() && isEmpty();
    }

    public boolean isEmpty() {
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }
}
