package com.gregtechceu.gtceu.uiwidgets.display;

import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.ProgressBar;
import com.gregtechceu.gtceu.uipro.elements.StatusLine;
import com.gregtechceu.gtceu.uipro.elements.StatusPanel;
import com.gregtechceu.gtceu.uipro.styletemplate.MachineEra;
import com.gregtechceu.gtceu.uipro.styletemplate.ScreenSprite;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 机器显示屏：屏幕底图上的读数行与进度条，按内容自适应高度。
 * 多方块主页与非多方块机器（维护仓等部件）共用，读数一律服务端求值下发。
 */
public final class MachineScreen {

    private static final int SCREEN_PADDING = 9;

    private final StatusPanel panel = new StatusPanel(LayoutStyle.AUTO);

    private MachineScreen() {
        panel.setBackground(MachineEra.CLASSIC.getScreen());
        panel.layout(l -> l.paddingAll(SCREEN_PADDING).gapAll(2));
    }

    public static MachineScreen create() {
        return new MachineScreen();
    }

    public MachineScreen setSprite(ScreenSprite sprite) {
        panel.setBackground(sprite);
        return this;
    }

    public StatusLine addLine(Component label, Supplier<Component> value) {
        return addLine(StatusLine.of(LayoutStyle.AUTO, label, value));
    }

    public StatusLine addLine(StatusLine line) {
        return panel.addLine(line.screenStyle());
    }

    public ProgressBar addBar(Component label, IntSupplier color, Supplier<ProgressBar.Progress> progress) {
        return addGauge(ProgressBar.of(LayoutStyle.AUTO, label, 0, progress).bindClientColor(color));
    }

    public ProgressBar addBar(Component label, int color, Supplier<ProgressBar.Progress> progress) {
        return addGauge(ProgressBar.of(LayoutStyle.AUTO, label, color, progress));
    }

    public ProgressBar addGauge(ProgressBar bar) {
        var gauge = new UIElement().layout(l -> l.width(LayoutStyle.AUTO).paddingAll(1));
        gauge.setBackground(UITheme.SCREEN_GAUGE_FRAME);
        gauge.addChild(bar);
        panel.addChild(gauge);
        return bar;
    }

    public MachineScreen addChild(Widget child) {
        panel.addChild(child);
        return this;
    }

    public StatusPanel getPanel() {
        return panel;
    }
}
