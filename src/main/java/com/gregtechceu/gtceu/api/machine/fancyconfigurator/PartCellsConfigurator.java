package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblockpro.PartCells;
import com.gregtechceu.gtceu.common.network.GTNetwork;
import com.gregtechceu.gtceu.common.network.RequestThrottle;
import com.gregtechceu.gtceu.common.network.packets.SPacketPartCells;
import com.gregtechceu.gtceu.uipro.data.RPC;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.UIChannel;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;
import com.gregtechceu.gtceu.uiwidgets.structure.PartCellsOverlay;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class PartCellsConfigurator implements IFancyConfiguratorButton {

    public static final String TITLE = "gtceu.gui.part_cells.title";
    public static final String LEGEND = "gtceu.gui.part_cells.legend";
    public static final String HINT = "gtceu.gui.part_cells.hint";
    public static final String HIDE = "gtceu.gui.part_cells.hide";
    public static final String NOT_FORMED = "gtceu.gui.part_cells.not_formed";
    public static final String NONE = "gtceu.gui.part_cells.none";
    public static final String TRUNCATED = "gtceu.gui.part_cells.truncated";
    private static final double MAX_DISTANCE_SQR = 64 * 64;

    private final IMultiController controller;
    private SyncValue<Boolean> formed;
    private RPC<Unit> request;

    private PartCellsConfigurator(IMultiController controller) {
        this.controller = controller;
    }

    public static void attach(ConfiguratorPanel panel, IFancyUIProvider provider) {
        if (provider instanceof IMultiController controller && controller.self().getDefinition() instanceof MultiblockMachineDefinition definition &&
                definition.hasStructure()) {
            panel.attachConfigurators(new PartCellsConfigurator(controller));
        }
    }

    @Override
    public void bindSync(UIChannel.Host host) {
        formed = host.addSyncValue(SyncValue.ofBool(controller::isFormed));
        request = host.addRPC(this::serverRequest).limit(1);
    }

    @Override
    public IGuiTexture getIcon() {
        return isShowing() ? WidgetIcons.PART_CELLS_ON : WidgetIcons.PART_CELLS_OFF;
    }

    @Override
    public List<Component> getTooltips() {
        if (isShowing()) return List.of(Component.translatable(TITLE), Component.translatable(HIDE).withStyle(ChatFormatting.GRAY));
        if (formed == null || !formed.getValue()) {
            return List.of(Component.translatable(TITLE), Component.translatable(NOT_FORMED).withStyle(ChatFormatting.RED));
        }
        return List.of(Component.translatable(TITLE), Component.translatable(LEGEND).withStyle(ChatFormatting.GRAY),
                Component.translatable(HINT).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void onClick(ClickData clickData) {
        if (clickData.isRemote) clientClick();
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public boolean isLatched() {
        return isShowing();
    }

    private boolean isShowing() {
        return controller.self().isRemote() && clientShowing();
    }

    @OnlyIn(Dist.CLIENT)
    private boolean clientShowing() {
        return PartCellsOverlay.isShowing(controller.self().getPos());
    }

    @OnlyIn(Dist.CLIENT)
    private void clientClick() {
        if (PartCellsOverlay.isShowing(controller.self().getPos())) {
            PartCellsOverlay.clear();
        } else if (formed.getValue()) {
            request.send(Unit.INSTANCE);
        }
    }

    private void serverRequest(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !controller.isFormed()) return;
        var machine = controller.self();
        if (machine.isRemoved() || machine.getLevel() != serverPlayer.level()) return;
        if (serverPlayer.distanceToSqr(Vec3.atCenterOf(machine.getPos())) > MAX_DISTANCE_SQR) return;
        if (!RequestThrottle.PART_CELLS.tryAcquire(serverPlayer)) return;
        var request = PartCells.request(controller);
        if (request == null) return;
        CompletableFuture.supplyAsync(() -> PartCells.candidates(request), Util.backgroundExecutor())
                .thenAcceptAsync(candidates -> deliver(serverPlayer, request, candidates), serverPlayer.server)
                .exceptionally(error -> {
                    GTCEu.LOGGER.error("Failed to collect part cells of {}", request.origin(), error);
                    return null;
                });
    }

    private void deliver(ServerPlayer player, PartCells.Request request, long[] candidates) {
        if (player.hasDisconnected()) return;
        var result = PartCells.finish(controller, request, candidates, player.blockPosition());
        if (result == null) return;
        if (result.shown() == 0) {
            player.displayClientMessage(Component.translatable(NONE), true);
            return;
        }
        if (result.isTruncated()) player.displayClientMessage(Component.translatable(TRUNCATED, result.shown(), result.total()), true);
        GTNetwork.NETWORK.sendToPlayer(new SPacketPartCells(result), player);
    }
}
