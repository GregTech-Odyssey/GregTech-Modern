package com.gregtechceu.gtceu.uipro.view;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Button;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 所有视图（2D 画布、流程图、3D 场景）共用的视图工具条，按 {@link ViewController} 取状态、执行操作。
 * 两种尺寸：标题栏（{@link #title}）与视图内悬浮栏（{@link #dock}）；按钮都只改本端视图。
 */
public final class ZoomBar extends UIElement {

    public static final Component ZOOM_IN = Component.translatable("gtceu.uipro.canvas.zoom_in");
    public static final Component ZOOM_OUT = Component.translatable("gtceu.uipro.canvas.zoom_out");
    public static final Component FIT = Component.translatable("gtceu.uipro.canvas.fit");
    public static final Component MINIMAP = Component.translatable("gtceu.uipro.canvas.minimap");
    public static final Component PERCENT = Component.translatable("gtceu.uipro.view.zoom_percent");
    public static final Component RESET = Component.translatable("gtceu.uipro.view.reset");
    public static final Component DISTANCE = Component.translatable("gtceu.uipro.view.zoom_distance");
    public static final Component ZOOM_LOCKED = Component.translatable("gtceu.uipro.view.zoom_locked");
    public static final Component ZOOM_LOCKED_HINT = Component.translatable("gtceu.uipro.view.zoom_locked.hint");
    public static final Component ZOOM_LOCKED_REASON = Component.translatable("gtceu.uipro.view.zoom_locked.reason");
    public static final Component ZOOM_UNLOCKED = Component.translatable("gtceu.uipro.view.zoom_unlocked");
    public static final Component ZOOM_UNLOCKED_HINT = Component.translatable("gtceu.uipro.view.zoom_unlocked.hint");
    private static final int PERCENT_WIDTH = 32;

    private final ViewController view;
    private final int size;
    private final List<Widget> parts = new ArrayList<>();
    private int width;

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
        var zoomOut = button(UITheme.CANVAS_ZOOM_OUT, ZOOM_OUT, () -> view.zoomStep(-1)).clientDisabled(() -> !view.canZoomOut());
        var zoomIn = button(UITheme.CANVAS_ZOOM_IN, ZOOM_IN, () -> view.zoomStep(1)).clientDisabled(() -> !view.canZoomIn());
        var group = new UIElement();
        group.addChild(zoomOut);
        int groupWidth = zoomOut.getSizeWidth() + UISizes.GAP + zoomIn.getSizeWidth();
        if (percent) {
            group.addChild(Button.of(PERCENT_WIDTH, size).bindClientText(view::percentText).setOnClientClick(view::percentClicked)
                    .tooltips(view.percentTooltip()));
            groupWidth += PERCENT_WIDTH + UISizes.GAP;
        }
        group.addChild(zoomIn);
        int total = groupWidth;
        group.layout(l -> l.row().size(total, size).gapAll(UISizes.GAP).alignCenter());
        group.clientDisabled(view::isZoomLocked, ZOOM_LOCKED_REASON);
        return part(group, total);
    }

    public ZoomBar lock() {
        var lock = Button.icon(size, WidgetIcons.VIEW_UNLOCKED)
                .bindClientIcon(() -> view.isZoomLocked() ? WidgetIcons.VIEW_LOCKED : WidgetIcons.VIEW_UNLOCKED);
        lock.setOnClientClick(() -> {
            view.setZoomLocked(!view.isZoomLocked());
            lockTooltips(lock);
        });
        lockTooltips(lock);
        return part(lock, size);
    }

    private void lockTooltips(Button lock) {
        boolean locked = view.isZoomLocked();
        lock.tooltips(locked ? ZOOM_LOCKED : ZOOM_UNLOCKED,
                (locked ? ZOOM_LOCKED_HINT : ZOOM_UNLOCKED_HINT).copy().withStyle(ChatFormatting.GRAY));
    }

    public ZoomBar fit(Component tooltip) {
        return add(button(UITheme.CANVAS_FIT, tooltip, view::fitView));
    }

    public ZoomBar minimap() {
        return add(button(UITheme.CANVAS_MINIMAP, MINIMAP, () -> view.setMinimapShown(!view.isMinimapShown()))
                .bindClientVariant(() -> view.isMinimapShown() && view.isMinimapAvailable() ? UITheme.ButtonVariant.CONFIRM : UITheme.ButtonVariant.DEFAULT)
                .clientDisabled(() -> !view.isMinimapAvailable()));
    }

    public ZoomBar add(Widget widget) {
        return part(widget, widget.getSizeWidth());
    }

    private ZoomBar part(Widget widget, int partWidth) {
        if (!parts.isEmpty()) width += UISizes.GAP;
        width += partWidth;
        parts.add(widget);
        return this;
    }

    public ZoomBar build() {
        int total = width;
        layout(l -> l.row().size(total, size).gapAll(UISizes.GAP).alignCenter());
        for (var part : parts) addChild(part);
        return this;
    }

    public Button button(IGuiTexture icon, Component tooltip, Runnable onClick) {
        return button(icon, tooltip, onClick, size);
    }

    public static Button button(IGuiTexture icon, Component tooltip, Runnable onClick, int size) {
        return Button.icon(size, icon).setOnClientClick(onClick).tooltips(tooltip);
    }

    public static Button dockButton(IGuiTexture icon, Component tooltip, Runnable onClick) {
        return button(icon, tooltip, onClick, UISizes.DOCK_BUTTON_SIZE);
    }

    public static UIElement dockRow() {
        return UIElement.centeredRow(UISizes.DOCK_BUTTON_SIZE);
    }
}
