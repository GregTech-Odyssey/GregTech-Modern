package com.gregtechceu.gtceu.uipro.utils;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.uipro.elements.TextField;
import com.gregtechceu.gtceu.uipro.window.MachineWindow;
import com.gregtechceu.gtceu.uipro.window.PageOverlay;

import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 框架的客户端界面事件。
 * <p>
 * 点击：LDLib 界面把鼠标点击按子控件倒序分发、遇到处理了的就停，获得焦点的输入框常常收不到"点在别处"的点击，
 * 不会失焦。在界面分发之前统一处理，让确认后提交的输入框（{@link TextField#commitOnSubmit}）点在外面就先提交——
 * 草稿的上行也因此排在这次点击触发的操作之前（如先写入输入的倍率、再执行"重置"）。
 * 随后通知点在外面的页内浮层（{@link PageOverlay#onOutsideClick}），弹出的小面板点哪里都关闭——先提交、后关闭。
 */
@Mod.EventBusSubscriber(modid = GTCEu.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class UIClientEvents {

    private UIClientEvents() {}

    /// 最高优先级、也收已取消的事件：EMI 等先取消了这次点击（如点在它的面板上）时，输入框也要先提交
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onMouseButtonPressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof ModularUIGuiContainer screen) {
            TextField.beforeGuiClick(screen, event.getMouseX(), event.getMouseY());
            MachineWindow.beforeGuiClick(screen.modularUI.mainGroup, event.getMouseX(), event.getMouseY());
        }
    }
}
