package com.gregtechceu.gtceu.uipro.canvas;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class ZoomTools extends UIElement {

    public static final String RESET = "gtceu.uipro.flow.zoom_reset";
    public static final String LOCK = "gtceu.uipro.flow.zoom_lock";
    private static final int SIZE = UISizes.ICON_BUTTON;
    private static final int PERCENT_WIDTH = 32;
    public static final int WIDTH = 3 * SIZE + PERCENT_WIDTH + 3 * UISizes.GAP;

    private final PanView view;
    private final Button zoomOut;
    private final Button percent;
    private final Button zoomIn;

    public ZoomTools(PanView view) {
        this.view = view;
        layout(l -> l.row().size(WIDTH, SIZE).gapAll(UISizes.GAP).alignCenter());
        zoomOut = Button.icon(UITheme.CANVAS_ZOOM_OUT, SIZE).setOnClientClick(() -> view.zoomStep(-1));
        zoomOut.setHoverTooltips(CanvasControls.ZOOM_OUT);
        percent = Button.text(PERCENT_WIDTH, SIZE, view::percentText).setOnClientClick(view::resetZoom);
        percent.setHoverTooltips(RESET);
        zoomIn = Button.icon(UITheme.CANVAS_ZOOM_IN, SIZE).setOnClientClick(() -> view.zoomStep(1));
        zoomIn.setHoverTooltips(CanvasControls.ZOOM_IN);
        var lock = Button.icon(UITheme.switching(view::isZoomLocked, WidgetIcons.ACCESS_PUBLIC, WidgetIcons.ACCESS_PRIVATE), SIZE)
                .setOnClientClick(view::toggleZoomLock)
                .setVariant(() -> view.isZoomLocked() ? UITheme.ButtonVariant.CONFIRM : UITheme.ButtonVariant.DEFAULT);
        lock.setHoverTooltips(LOCK);
        addChildren(zoomOut, percent, zoomIn, lock);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        if (!view.isZoomLocked()) return;
        drawDisabled(graphics, zoomOut);
        drawDisabled(graphics, percent);
        drawDisabled(graphics, zoomIn);
    }

    @OnlyIn(Dist.CLIENT)
    private static void drawDisabled(GuiGraphics graphics, Button button) {
        UITheme.drawDisabled(graphics, button.getPositionX(), button.getPositionY(), button.getSizeWidth(), button.getSizeHeight());
    }
}
