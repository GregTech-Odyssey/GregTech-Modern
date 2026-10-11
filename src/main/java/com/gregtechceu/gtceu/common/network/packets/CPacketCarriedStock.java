package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.api.machine.multiblockpro.PlayerSupply;
import com.gregtechceu.gtceu.common.network.GTNetwork;
import com.gregtechceu.gtceu.common.network.RequestThrottle;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CPacketCarriedStock implements IPacket {

    public static final int MAX_WANTED = 1024;

    private List<Item> wanted = new ArrayList<>();
    private boolean network;

    public CPacketCarriedStock() {}

    public CPacketCarriedStock(boolean network, Collection<Item> wanted) {
        this.network = network;
        this.wanted = new ArrayList<>(network ? wanted : List.of());
        if (this.wanted.size() > MAX_WANTED) this.wanted = this.wanted.subList(0, MAX_WANTED);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(network);
        buf.writeVarInt(wanted.size());
        for (var item : wanted) buf.writeId(BuiltInRegistries.ITEM, item);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        network = buf.readBoolean();
        int size = Math.min(buf.readVarInt(), MAX_WANTED);
        wanted = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            var item = buf.readById(BuiltInRegistries.ITEM);
            if (item != null && item != Items.AIR) wanted.add(item);
        }
    }

    @Override
    public void execute(IHandlerContext handler) {
        if (!(handler.getPlayer() instanceof ServerPlayer player) || !RequestThrottle.CARRIED_STOCK.tryAcquire(player)) return;
        var supply = PlayerSupply.of(player, true, network);
        var counts = supply.count();
        supply.countNetwork(counts, wanted);
        GTNetwork.NETWORK.sendToPlayer(new SPacketCarriedStock(counts), player);
    }
}
