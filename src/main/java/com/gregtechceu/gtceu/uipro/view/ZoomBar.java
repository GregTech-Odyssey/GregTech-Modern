package com.gregtechceu.gtceu.uipro.view;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import java.util.ArrayList;
import java.util.List;

/**
 * 所有视图（2D 画布、流程图、3D 场景）共用的视图工具条，按 {@link ViewController} 取状态、执行操作。
 * 两种尺寸：标题栏（{@link #title}）与视图内悬浮栏（{@link #dock}）；按钮都只改本端视图。
 */
public final class ZoomBar extends UIElement {

    public static final String ZOOM_IN = "gtceu.uipro.canvas.zoom_in";
    public static final String ZOOM_OUT = "gtceu.uipro.canvas.zoom_out";
    public static final String FIT = "gtceu.uipro.canvas.fit";
    public static final String MINIMAP = "gtceu.uipro.canvas.minimap";
    public static final String PERCENT = "gtceu.uipro.view.zoom_percent";
    public static final String RESET = "gtceu.uipro.view.reset";
    public static final String DISTANCE = "gtceu.uipro.view.zoom_distance";
    private static final int PERCENT_WIDTH = 32;

    private final ViewController view;
    private final int size;
    private final List<Widget> parts = new ArrayList<>();

    private ZoomBar(ViewController view, int size) {
        this.view = view;
        this.size = size;
    }

    public static ZoomBar title(ViewController view) {
        return new ZoomBar(view, UISizes.ICON_BUTTON_SIZE).zoom(true).build();
    }

    public static ZoomBar dock(ViewController view) {
        return new ZoomBar(view, UISizes.DOCK_BUTTON_SIZE).zoom(false).fit(FIT).minimap().build();
    }

    public static ZoomBar of(ViewController view, boolean dock) {
        return new ZoomBar(view, dock ? UISizes.DOCK_BUTTON_SIZE : UISizes.ICON_BUTTON_SIZE);
    }

    public ZoomBar zoom(boolean percent) {
        parts.add(button(UITheme.CANVAS_ZOOM_OUT, ZOOM_OUT, () -> view.zoomStep(-1)).clientDisabled(() -> !view.canZoomOut()));
        if (percent) {
            var label = Button.of(PERCENT_WIDTH, size).bindClientText(view::percentText).setOnClientClick(view::percentClicked)
                    .tooltips(view.percentTooltip());
            parts.add(label);
        }
        parts.add(button(UITheme.CANVAS_ZOOM_IN, ZOOM_IN, () -> view.zoomStep(1)).clientDisabled(() -> !view.canZoomIn()));
        return this;
    }

    public ZoomBar fit(String tooltipKey) {
        parts.add(button(UITheme.CANVAS_FIT, tooltipKey, view::fitView));
        return this;
    }

    public ZoomBar minimap() {
        parts.add(button(UITheme.CANVAS_MINIMAP, MINIMAP, () -> view.setMinimapShown(!view.isMinimapShown()))
                .bindClientVariant(() -> view.isMinimapShown() && view.isMinimapAvailable() ? UITheme.ButtonVariant.CONFIRM : UITheme.ButtonVariant.DEFAULT)
                .clientDisabled(() -> !view.isMinimapAvailable()));
        return this;
    }

    public ZoomBar add(Widget widget) {
        parts.add(widget);
        return this;
    }

    public ZoomBar build() {
        int width = 0;
        for (var part : parts) width += part.getSizeWidth();
        width += Math.max(0, parts.size() - 1) * UISizes.GAP;
        int total = width;
        layout(l -> l.row().size(total, size).gapAll(UISizes.GAP).alignCenter());
        for (var part : parts) addChild(part);
        return this;
    }

    public Button button(IGuiTexture icon, String tooltipKey, Runnable onClick) {
        return button(icon, tooltipKey, onClick, size);
    }

    public static Button button(IGuiTexture icon, String tooltipKey, Runnable onClick, int size) {
        return Button.icon(size, icon).setOnClientClick(onClick).tooltips(tooltipKey);
    }

    public static Button dockButton(IGuiTexture icon, String tooltipKey, Runnable onClick) {
        return button(icon, tooltipKey, onClick, UISizes.DOCK_BUTTON_SIZE);
    }

    public static UIElement dockRow() {
        return UIElement.centeredRow(UISizes.DOCK_BUTTON_SIZE);
    }
}
