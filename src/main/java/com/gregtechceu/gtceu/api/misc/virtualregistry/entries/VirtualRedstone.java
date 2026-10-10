package com.gregtechceu.gtceu.api.misc.virtualregistry.entries;

import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;

import net.minecraft.nbt.CompoundTag;

import com.gto.datasynclib.datastream.codec.ValueOps;
import com.gto.datasynclib.datastream.codec.ValueCodec;
import com.gto.datasynclib.util.ValueCodecs;
import it.unimi.dsi.fastutil.objects.Object2ShortMap;
import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class VirtualRedstone extends VirtualEntry {

    public static final ValueCodec<VirtualRedstone> DATA_CODEC = new ValueCodec<>() {

        @Override
        public VirtualRedstone decode(ValueOps ops, @NotNull Object data) {
            var tank = new VirtualRedstone();
            tank.deserializeNBT(ValueCodecs.COMPOUND_TAG.decode(ops, data));
            return tank;
        }

        @Override
        public @NotNull Object encode(ValueOps ops, VirtualRedstone obj) {
            return ValueCodecs.COMPOUND_TAG.encode(ops, obj.serializeNBT());
        }
    };

    private static final String MEMBERS_KEY = "members";

    @Getter
    private final Object2ShortOpenHashMap<UUID> members = new Object2ShortOpenHashMap<>();

    public VirtualRedstone() {}

    public int getSignal() {
        return members.values().intStream().max().orElse(0);
    }

    public void addMember(UUID uuid) {
        members.put(uuid, (short) 0);
    }

    public void setSignal(UUID uuid, int signal) {
        if (!members.containsKey(uuid)) return;
        members.put(uuid, (short) signal);
    }

    public void removeMember(UUID uuid) {
        members.removeShort(uuid);
    }

    @Override
    public EntryTypes<? extends VirtualEntry> getType() {
        return EntryTypes.ENDER_REDSTONE;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        CompoundTag tag2 = new CompoundTag();
        for (ObjectIterator<Object2ShortMap.Entry<UUID>> it = members.object2ShortEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            tag2.putShort(entry.getKey().toString(), entry.getShortValue());
        }
        tag.put(MEMBERS_KEY, tag2);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        super.deserializeNBT(nbt);
        CompoundTag tag = nbt.getCompound(MEMBERS_KEY);
        for (String uuid : tag.getAllKeys()) {
            members.put(UUID.fromString(uuid), tag.getShort(uuid));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VirtualRedstone other)) return false;
        return other.members == this.members;
    }

    @Override
    public boolean canRemove() {
        return super.canRemove() && members.isEmpty();
    }
}
