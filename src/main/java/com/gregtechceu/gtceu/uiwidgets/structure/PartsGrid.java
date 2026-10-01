package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.style.FlexWrap;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
final class PartsGrid extends UIElement {

    private final Consumer<Item> onToggle;
    private final int minColumns;
    private final List<Item> items = new ArrayList<>();
    private Set<Item> shown = Collections.emptySet();
    private int columns;

    PartsGrid(Consumer<Item> onToggle, int minColumns) {
        this.onToggle = onToggle;
        this.minColumns = minColumns;
        layout(l -> l.row().flexWrap(FlexWrap.WRAP).width(minColumns * UISizes.SLOT_SIZE).minHeight(UISizes.SLOT_SIZE));
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
        int width = Math.min(columns, Math.max(minColumns, widgets.size())) * UISizes.SLOT_SIZE;
        layout(l -> l.width(width));
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
    public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        if (GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS) return null;
        return super.getXEIIngredientOverMouse(mouseX, mouseY);
    }

    @Override
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (shown.isEmpty()) return;
        for (int i = 0; i < widgets.size(); i++) {
            if (!shown.contains(items.get(i))) continue;
            var slot = widgets.get(i);
            UIDraw.selectionFrame(graphics, slot.getPositionX(), slot.getPositionY(), slot.getSizeWidth(), slot.getSizeHeight());
        }
    }
}
