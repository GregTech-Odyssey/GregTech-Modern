package com.gregtechceu.gtceu.client.forge;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.client.TooltipsHandler;
import com.gregtechceu.gtceu.client.renderer.BlockHighlightRenderer;
import com.gregtechceu.gtceu.client.renderer.MultiblockInWorldPreviewRenderer;
import com.gregtechceu.gtceu.client.util.TooltipHelper;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.InfiniteSprayCanBehaviour;
import com.gregtechceu.gtceu.common.network.GTNetwork;
import com.gregtechceu.gtceu.common.network.packets.CPacketSprayCanAction;
import com.gregtechceu.gtceu.integration.map.ClientCacheManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = GTCEu.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class ForgeClientEventListener {

    @SubscribeEvent
    public static void onRenderLevelStageEvent(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            // to render the preview after block entities, before the translucent. so it can be seen through the
            // transparent blocks.
            MultiblockInWorldPreviewRenderer.renderInWorldPreview(event.getPoseStack(), event.getCamera());
        }
    }

    @SubscribeEvent
    public static void onBlockHighlightEvent(RenderHighlightEvent.Block event) {
        BlockHighlightRenderer.renderBlockHighlight(event.getPoseStack(), event.getCamera(), event.getTarget(),
                event.getMultiBufferSource(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onTooltipEvent(ItemTooltipEvent event) {
        TooltipsHandler.appendTooltips(event.getItemStack(), event.getFlags(), event.getToolTip());
    }

    @SubscribeEvent
    public static void onClientTickEvent(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            TooltipHelper.onClientTick();
            MultiblockInWorldPreviewRenderer.onClientTick();
            GTValues.CLIENT_TIME++;
        }
    }

    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        if (event.getPlayer() != null) {
            ClientCacheManager.saveCaches();
            ClientCacheManager.clearCaches();
            ClientCacheManager.allowReinit();
        }
    }

    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        ClientCacheManager.clearCaches();
    }

    /**
     * Sneak + scroll while holding the infinite spray can cycles color, matching AE2's
     * {@code ColorApplicator} / {@code AppEngClient#wheelEvent} pattern: only while shift is held,
     * cancel the scroll so the hotbar does not change, apply client-side for responsiveness, and
     * sync to the server.
     */
    @SubscribeEvent
    public static void onSprayCanScroll(InputEvent.MouseScrollingEvent event) {
        if (event.getScrollDelta() == 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) {
            return;
        }
        // AE uses isShiftKeyDown (not pose-only isCrouching) as "alternate use mode"
        if (!player.isShiftKeyDown()) {
            return;
        }

        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = player.getMainHandItem();
        if (!stack.is(GTItems.INFINITE_SPRAY_CAN.get())) {
            stack = player.getOffhandItem();
            hand = InteractionHand.OFF_HAND;
            if (!stack.is(GTItems.INFINITE_SPRAY_CAN.get())) {
                return;
            }
        }

        // scroll up → next color; scroll down → previous (same sign as AE MouseWheelPacket)
        int direction = event.getScrollDelta() > 0 ? 1 : -1;
        InfiniteSprayCanBehaviour.cycle(stack, direction);
        InfiniteSprayCanBehaviour.playColorSwitchSound(player);
        GTNetwork.NETWORK.sendToServer(
                new CPacketSprayCanAction(CPacketSprayCanAction.ACTION_CYCLE, hand, direction));
        event.setCanceled(true);
    }
}
