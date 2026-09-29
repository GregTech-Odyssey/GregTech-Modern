package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
final class PartsGrid extends WidgetGroup {

    private final Consumer<Item> onToggle;
    private final int minColumns;
    private final List<Item> items = new ArrayList<>();
    private Set<Item> shown = Collections.emptySet();
    private int columns;

    PartsGrid(Consumer<Item> onToggle, int minColumns) {
        super(0, 0, minColumns * UISizes.SLOT, UISizes.SLOT);
        this.onToggle = onToggle;
        this.minColumns = minColumns;
    }

    void fill(List<ItemStack> stacks, List<SlotWidget> slots, Set<Item> shown, int columns) {
        clearAllWidgets();
        items.clear();
        for (var stack : stacks) items.add(stack.getItem());
        this.shown = shown;
        for (var slot : slots) addWidget(slot);
        this.columns = 0;
        arrange(columns);
    }

    void arrange(int columns) {
        if (columns == this.columns) return;
        this.columns = columns;
        for (int i = 0; i < widgets.size(); i++) {
            widgets.get(i).setSelfPosition(new Position((i % columns) * UISizes.SLOT, (i / columns) * UISizes.SLOT));
        }
        int rows = Math.max(1, (widgets.size() + columns - 1) / columns);
        setSize(new Size(Math.min(columns, Math.max(minColumns, widgets.size())) * UISizes.SLOT, rows * UISizes.SLOT));
        UIElement.markLayoutDirty(this);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            for (int i = 0; i < widgets.size(); i++) {
                if (widgets.get(i).isMouseOverElement(mouseX, mouseY)) {
                    playButtonClickSound();
                    onToggle.accept(items.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (shown.isEmpty()) return;
        for (int i = 0; i < widgets.size(); i++) {
            if (!shown.contains(items.get(i))) continue;
            var slot = widgets.get(i);
            UITheme.drawSelection(graphics, slot.getPositionX(), slot.getPositionY(), slot.getSizeWidth(), slot.getSizeHeight());
        }
    }
}
