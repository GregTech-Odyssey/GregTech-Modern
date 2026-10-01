package com.gregtechceu.gtceu.uipro;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;

@OnlyIn(Dist.CLIENT)
public final class UIInput {

    @Nullable
    private static WeakReference<Widget> captured;
    private static int capturedButton = -1;

    private UIInput() {}

    public static void capture(Widget widget, int button) {
        captured = new WeakReference<>(widget);
        capturedButton = button;
    }

    public static void release(Widget widget) {
        if (captured != null && captured.get() == widget) clear();
    }

    public static int capturedButton() {
        return capturedButton;
    }

    @Nullable
    public static Widget captured() {
        if (captured == null) return null;
        var widget = captured.get();
        if (widget == null || widget.getGui() == null) {
            clear();
            return null;
        }
        return widget;
    }

    @Nullable
    public static Widget capturedIn(ModularUI ui) {
        var widget = captured();
        return widget != null && widget.getGui() == ui ? widget : null;
    }

    public static void clear() {
        captured = null;
        capturedButton = -1;
    }

    public static double[] toLocal(Widget target, double x, double y) {
        var chain = new java.util.ArrayList<UIElement>();
        for (var current = target.getParent(); current != null; current = current.getParent()) {
            if (current instanceof UIElement element && element.getTransform() != null) chain.add(element);
        }
        double scale = 1;
        for (int i = chain.size() - 1; i >= 0; i--) {
            var element = chain.get(i);
            x = element.toLocalX(x);
            y = element.toLocalY(y);
            scale *= element.getTransform().getScale();
        }
        return new double[] { x, y, scale };
    }
}
