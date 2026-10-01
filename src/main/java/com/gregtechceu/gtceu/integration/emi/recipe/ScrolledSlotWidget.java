package com.gregtechceu.gtceu.integration.emi.recipe;

import com.gregtechceu.gtceu.uipro.render.UIClip;

import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.jei.ModularWrapper;

import net.minecraft.client.gui.GuiGraphics;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import org.jetbrains.annotations.Nullable;

public class ScrolledSlotWidget extends SlotWidget {

    private final Widget slot;
    private final DraggableScrollableWidgetGroup scroller;
    private final ModularWrapper<?> modular;

    public ScrolledSlotWidget(EmiIngredient stack, Widget slot, DraggableScrollableWidgetGroup scroller, ModularWrapper<?> modular) {
        super(stack, slot.getPosition().x, slot.getPosition().y);
        this.slot = slot;
        this.scroller = scroller;
        this.modular = modular;
    }

    @Nullable
    public static DraggableScrollableWidgetGroup findScroller(Widget widget) {
        for (var parent = widget.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof DraggableScrollableWidgetGroup scroller && scroller.isUseScissor()) return scroller;
        }
        return null;
    }

    private Bounds slotBounds() {
        var position = slot.getPosition();
        var size = slot.getSize();
        return new Bounds(position.x - modular.getLeft(), position.y - modular.getTop(), size.width, size.height);
    }

    @Override
    public Bounds getBounds() {
        var bounds = slotBounds();
        var position = scroller.getPosition();
        var size = scroller.getSize();
        int x = position.x - modular.getLeft(), y = position.y - modular.getTop();
        int left = Math.max(bounds.left(), x);
        int top = Math.max(bounds.top(), y);
        int right = Math.min(bounds.right(), x + size.width);
        int bottom = Math.min(bounds.bottom(), y + size.height);
        return right > left && bottom > top ? new Bounds(left, top, right - left, bottom - top) : Bounds.EMPTY;
    }

    @Override
    public void render(GuiGraphics draw, int mouseX, int mouseY, float delta) {
        var clip = getBounds();
        if (clip.empty()) return;
        UIClip.push(draw, clip.left(), clip.top(), clip.width(), clip.height());
        try {
            super.render(draw, mouseX, mouseY, delta);
        } finally {
            UIClip.pop(draw);
        }
    }

    @Override
    public void drawStack(GuiGraphics draw, int mouseX, int mouseY, float delta) {
        var bounds = slotBounds();
        getStack().render(draw, bounds.x() + (bounds.width() - 16) / 2, bounds.y() + (bounds.height() - 16) / 2, delta);
    }
}
