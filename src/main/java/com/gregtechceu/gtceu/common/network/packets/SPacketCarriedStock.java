package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.uiwidgets.patternbuilder.CarriedStock;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;

import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

public class SPacketCarriedStock implements IPacket {

    private Reference2LongOpenHashMap<Item> counts = new Reference2LongOpenHashMap<>();

    public SPacketCarriedStock() {}

    public SPacketCarriedStock(Reference2LongOpenHashMap<Item> counts) {
        this.counts = counts;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(counts.size());
        for (var it = counts.reference2LongEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            buf.writeVarInt(BuiltInRegistries.ITEM.getId(entry.getKey()));
            buf.writeVarLong(entry.getLongValue());
        }
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 0 || size > buf.readableBytes() / 2) {
            counts = new Reference2LongOpenHashMap<>();
            return;
        }
        counts = new Reference2LongOpenHashMap<>(size);
        for (int i = 0; i < size; i++) counts.put(BuiltInRegistries.ITEM.byId(buf.readVarInt()), buf.readVarLong());
    }

    @Override
    public void execute(IHandlerContext handler) {
        CarriedStock.accept(counts);
    }
}
