package com.gregtechceu.gtceu.uipro.canvas;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

public class CanvasZoomTools extends UIElement {

    private static final int SIZE = UISizes.ICON_BUTTON;
    private static final int PERCENT_WIDTH = 32;
    public static final int WIDTH = 2 * SIZE + PERCENT_WIDTH + 2 * UISizes.GAP;

    private final CanvasView canvas;
    private float percentScale = -1;
    private String percentText = "";

    public CanvasZoomTools(CanvasView canvas) {
        this.canvas = canvas;
        layout(l -> l.row().size(WIDTH, SIZE).gapAll(UISizes.GAP).alignCenter());
        var zoomOut = Button.icon(UITheme.CANVAS_ZOOM_OUT, SIZE).setOnClientClick(() -> canvas.zoomBy(1 / CanvasView.BUTTON_ZOOM_STEP, true));
        zoomOut.setHoverTooltips(CanvasControls.ZOOM_OUT);
        var percent = Button.text(PERCENT_WIDTH, SIZE, this::percentText).setOnClientClick(() -> canvas.fitContent(true));
        percent.setHoverTooltips(CanvasControls.FIT);
        var zoomIn = Button.icon(UITheme.CANVAS_ZOOM_IN, SIZE).setOnClientClick(() -> canvas.zoomBy(CanvasView.BUTTON_ZOOM_STEP, true));
        zoomIn.setHoverTooltips(CanvasControls.ZOOM_IN);
        addChildren(zoomOut, percent, zoomIn);
    }

    private String percentText() {
        float scale = canvas.scale();
        if (percentScale != scale) {
            percentScale = scale;
            percentText = Math.round(scale * 100) + "%";
        }
        return percentText;
    }
}
