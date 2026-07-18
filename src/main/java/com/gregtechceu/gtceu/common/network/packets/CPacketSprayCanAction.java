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
 * Client → server actions for the infinite spray can that cannot use normal item hooks.
 * Currently used for sneak+scroll color cycling (AE Color Applicator style). Palette open is
 * handled by sneak+right-click via the item {@code use}/{@code onItemUseFirst} path.
 */
public class CPacketSprayCanAction implements IPacket {

    public static final byte ACTION_CYCLE = 0;
    /** Palette opens via sneak+right-click; retained for packet compatibility. */
    public static final byte ACTION_OPEN_GUI = 1;

    private byte action;
    private boolean mainHand;
    /** Cycle direction: +1 forward (scroll up), -1 reverse (scroll down). Ignored for OPEN_GUI. */
    private byte direction = 1;

    public CPacketSprayCanAction() {}

    public CPacketSprayCanAction(byte action, InteractionHand hand) {
        this(action, hand, 1);
    }

    public CPacketSprayCanAction(byte action, InteractionHand hand, int direction) {
        this.action = action;
        this.mainHand = hand == InteractionHand.MAIN_HAND;
        this.direction = (byte) (direction < 0 ? -1 : 1);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action);
        buf.writeBoolean(mainHand);
        buf.writeByte(direction);
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        this.action = buf.readByte();
        this.mainHand = buf.readBoolean();
        this.direction = buf.readByte() < 0 ? (byte) -1 : (byte) 1;
    }

    @Override
    public void execute(IHandlerContext handler) {
        if (!(handler.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isSpectator() || !player.isAlive()) {
            return;
        }
        InteractionHand hand = mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(GTItems.INFINITE_SPRAY_CAN.get())) {
            return;
        }
        if (action == ACTION_CYCLE) {
            InfiniteSprayCanBehaviour.cycle(stack, direction);
            // Nearby players hear the shake; the cycling player already played client-side.
            InfiniteSprayCanBehaviour.playColorSwitchSound(player);
        } else if (action == ACTION_OPEN_GUI) {
            HeldItemUIFactory.INSTANCE.openUI(player, hand);
        }
    }
}
