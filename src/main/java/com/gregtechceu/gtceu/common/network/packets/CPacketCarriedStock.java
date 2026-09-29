package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.api.machine.multiblockpro.PlayerSupply;
import com.gregtechceu.gtceu.common.network.GTNetwork;
import com.gregtechceu.gtceu.common.network.RequestThrottle;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public class CPacketCarriedStock implements IPacket {

    public CPacketCarriedStock() {}

    @Override
    public void encode(FriendlyByteBuf buf) {}

    @Override
    public void decode(FriendlyByteBuf buf) {}

    @Override
    public void execute(IHandlerContext handler) {
        if (!(handler.getPlayer() instanceof ServerPlayer player) || !RequestThrottle.CARRIED_STOCK.tryAcquire(player)) return;
        GTNetwork.NETWORK.sendToPlayer(new SPacketCarriedStock(PlayerSupply.of(player, true).count()), player);
    }
}
