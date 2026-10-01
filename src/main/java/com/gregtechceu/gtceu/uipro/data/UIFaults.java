package com.gregtechceu.gtceu.uipro.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIContainer;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 服务端界面故障的统一处理：记录、关闭出错玩家的界面、提示玩家，代替崩服或静默吞掉。
 */
public final class UIFaults {

    public static final String OPEN_FAILED = "gtceu.uipro.fault.open";
    public static final String CLOSED = "gtceu.uipro.fault.closed";

    private UIFaults() {}

    public static void openFailed(Object factory, Object holder, ServerPlayer player, AbstractContainerMenu previous, Throwable error) {
        GTCEu.LOGGER.error("UI open failed: factory={} holder={} player={}", factory.getClass().getName(), describe(holder),
                player.getGameProfile().getName(), error);
        if (player.containerMenu != previous || player.containerMenu == player.inventoryMenu) closeSafely(player);
        player.sendSystemMessage(Component.translatable(OPEN_FAILED).withStyle(ChatFormatting.RED));
    }

    public static void syncFailed(ModularUI ui, Throwable error) {
        GTCEu.LOGGER.error("UI sync failed, closing: holder={} player={}", describe(ui.holder), playerName(ui), error);
        close(ui);
    }

    public static void actionFailed(ModularUI ui, Throwable error) {
        GTCEu.LOGGER.error("UI client action failed, closing: holder={} player={}", describe(ui.holder), playerName(ui), error);
        close(ui);
    }

    public static void release(ModularUI ui) {
        try {
            ui.triggerCloseListeners();
        } catch (RuntimeException | LinkageError e) {
            GTCEu.LOGGER.error("UI close listeners failed: holder={}", describe(ui.holder), e);
        }
    }

    private static void close(ModularUI ui) {
        if (!(ui.entityPlayer instanceof ServerPlayer player)) return;
        if (player.containerMenu instanceof ModularUIContainer container && container.getModularUI() == ui) {
            closeSafely(player);
            player.sendSystemMessage(Component.translatable(CLOSED).withStyle(ChatFormatting.RED));
        }
    }

    private static void closeSafely(ServerPlayer player) {
        try {
            player.closeContainer();
        } catch (RuntimeException | LinkageError e) {
            GTCEu.LOGGER.error("Closing UI failed for {}", player.getGameProfile().getName(), e);
            player.containerMenu = player.inventoryMenu;
        }
    }

    private static String playerName(ModularUI ui) {
        return ui.entityPlayer == null ? "?" : ui.entityPlayer.getGameProfile().getName();
    }

    public static String where(Object owner) {
        if (owner instanceof Widget widget && widget.getGui() != null) return owner.getClass().getName() + " in " + describe(widget.getGui().holder);
        return owner.getClass().getName();
    }

    private static String describe(Object holder) {
        if (holder instanceof MetaMachine machine) return machine.getDefinition().getId() + "@" + machine.getPos().toShortString();
        return holder == null ? "null" : holder.getClass().getName();
    }
}
