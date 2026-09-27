package com.gregtechceu.gtceu.uipro;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

public interface IHoverOwner {

    boolean ownsHover(int mouseX, int mouseY);

    static boolean anyOwns(WidgetGroup group, int mouseX, int mouseY) {
        for (Widget widget : group.widgets) {
            if (!widget.isVisible()) continue;
            if (widget instanceof IHoverOwner owner && owner.ownsHover(mouseX, mouseY)) return true;
            if (widget instanceof WidgetGroup child && anyOwns(child, mouseX, mouseY)) return true;
        }
        return false;
    }
}
