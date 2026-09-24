package com.gregtechceu.gtceu.uipro.canvas;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.network.chat.Component;

/**
 * 画布的标准视图按钮，放进悬浮在画布上的 {@code Dock}：缩小、放大、适应全部、缩略图开关。
 * <p>
 * 都只改本端的视图（客户端），不发请求；缩略图开着时按钮整块绿色（与图标开关 {@code IconToggle} 一致）。
 * 两端建界面时都要创建（按钮是控件树的一部分）。
 */
public final class CanvasControls {

    public static final String ZOOM_IN = "gtceu.uipro.canvas.zoom_in";
    public static final String ZOOM_OUT = "gtceu.uipro.canvas.zoom_out";
    public static final String FIT = "gtceu.uipro.canvas.fit";
    public static final String MINIMAP = "gtceu.uipro.canvas.minimap";

    private CanvasControls() {}

    /** 一组横排的视图按钮。 */
    public static UIElement of(CanvasView canvas) {
        var zoomOut = button(UITheme.CANVAS_ZOOM_OUT, ZOOM_OUT, () -> canvas.zoomBy(1 / CanvasView.BUTTON_ZOOM_STEP, true));
        var zoomIn = button(UITheme.CANVAS_ZOOM_IN, ZOOM_IN, () -> canvas.zoomBy(CanvasView.BUTTON_ZOOM_STEP, true));
        var fit = button(UITheme.CANVAS_FIT, FIT, () -> canvas.fitContent(true));
        var minimap = button(UITheme.CANVAS_MINIMAP, MINIMAP, () -> canvas.setMinimapShown(!canvas.isMinimapShown()))
                .setVariant(() -> canvas.isMinimapShown() ? UITheme.ButtonVariant.CONFIRM : UITheme.ButtonVariant.DEFAULT);
        return row().addChildren(zoomOut, zoomIn, fit, minimap);
    }

    /**
     * 与标准视图按钮同样式的按钮（使用方往悬浮栏里加自己的按钮，如"定位到选中项"）：{@link UISizes#DOCK_BUTTON} 见方，
     * 16×16 图标原尺寸显示；只在客户端执行。
     */
    public static Button button(IGuiTexture icon, String tooltipKey, Runnable onClick) {
        var button = Button.icon(icon, UISizes.DOCK_BUTTON).setOnClientClick(onClick);
        button.setHoverTooltips(Component.translatable(tooltipKey));
        return button;
    }

    /** 悬浮栏里一组按钮的横排容器。 */
    public static UIElement row() {
        return UIElement.row(UISizes.DOCK_BUTTON).layout(l -> l.gapAll(UISizes.GAP).alignCenter());
    }
}
