package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.InfiniteSprayCanBehaviour;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;

/**
 * Sent from the client when the player left clicks (cycle color) or middle clicks (open palette)
 * while holding the infinite spray can. The mouse buttons cannot be reacted to with the normal
 * item hooks, so the client intercepts them and forwards the intent here.
 */
public class CPacketSprayCanAction implements IPacket {

    public static final byte ACTION_CYCLE = 0;
    public static final byte ACTION_OPEN_GUI = 1;

    private byte action;
    private boolean mainHand;

    public CPacketSprayCanAction() {}

    public CPacketSprayCanAction(byte action, InteractionHand hand) {
        this.action = action;
        this.mainHand = hand == InteractionHand.MAIN_HAND;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeBoolean(mainHand);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        this.action = buf.readByte();
        this.mainHand = buf.readBoolean();
    }

    @Override
    public void execute(IHandlerContext handler) {
        if (!(handler.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        InteractionHand hand = mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(GTItems.INFINITE_SPRAY_CAN.get())) {
            return;
        }
        if (action == ACTION_CYCLE) {
            InfiniteSprayCanBehaviour.cycle(stack, 1);
        } else if (action == ACTION_OPEN_GUI) {
            HeldItemUIFactory.INSTANCE.openUI(player, hand);
        }
    }
}
