package com.gregtechceu.gtceu.uipro.window;

import com.gregtechceu.gtceu.uipro.ILayoutHost;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UIStyleManager;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

/**
 * 占满整个屏幕的服务端界面根：客户端按 GUI 缩放后的屏幕尺寸设定界面与自身大小，屏幕尺寸变化时重新布局。
 * 可选的配色方案覆盖在客户端打开时压栈、关闭时出栈。
 */
public class ScreenHost extends UIElement implements ILayoutHost {

    public static final int SERVER_WIDTH = 176, SERVER_HEIGHT = 166;

    @Nullable
    private ResourceLocation styleScheme, stylePack;
    private boolean stylePushed;
    private int screenWidth = SERVER_WIDTH, screenHeight = SERVER_HEIGHT;

    public ScreenHost() {
        layout(l -> l.size(SERVER_WIDTH, SERVER_HEIGHT));
        listenUIClose();
    }

    public static ModularUI createUI(ScreenHost host, IUIHolder holder, Player player) {
        return new ModularUI(SERVER_WIDTH, SERVER_HEIGHT, holder, player).widget(host);
    }

    public ScreenHost setStyleOverride(ResourceLocation colorScheme, ResourceLocation texturePack) {
        this.styleScheme = colorScheme;
        this.stylePack = texturePack;
        return this;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    protected void onScreenResized(int width, int height) {}

    @Override
    public void onContentResized(Widget root) {}

    @Override
    public void initWidget() {
        super.initWidget();
        if (isRemote()) clientInit();
    }

    @OnlyIn(Dist.CLIENT)
    protected void clientInit() {
        if (styleScheme != null && stylePack != null && !stylePushed) {
            UIStyleManager.pushOverride(styleScheme, stylePack);
            stylePushed = true;
        }
        syncScreenSize();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateScreen() {
        syncScreenSize();
        super.updateScreen();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        syncScreenSize();
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void onUIClosed() {
        if (isRemote()) clientClose();
    }

    @OnlyIn(Dist.CLIENT)
    private void clientClose() {
        if (!stylePushed) return;
        stylePushed = false;
        UIStyleManager.popOverride();
    }

    @OnlyIn(Dist.CLIENT)
    private void syncScreenSize() {
        var gui = getGui();
        if (gui == null) return;
        var window = Minecraft.getInstance().getWindow();
        int width = window.getGuiScaledWidth(), height = window.getGuiScaledHeight();
        if (width == screenWidth && height == screenHeight && gui.getWidth() == width && gui.getHeight() == height) return;
        screenWidth = width;
        screenHeight = height;
        layout(l -> l.size(width, height));
        gui.setSize(width, height);
        onScreenResized(width, height);
    }
}
