package com.gregtechceu.gtceu.uiwidgets.recipe;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

public class RecipeTierChip extends UIElement {

    private static final int ARROW_WIDTH = 5;
    private static final int ARROW_GAP = 3;
    private static final int ARROW_COLOR = 0xFF8A8A8A;
    private static final int ARROW_HOVER = 0xFF202020;

    private final IntSupplier tier;
    private final IntConsumer setTier;
    private final int min, max;
    private final IntFunction<Component> name;
    private final List<Component> tooltip;

    public RecipeTierChip(IntSupplier tier, IntConsumer setTier, int min, int max, IntFunction<Component> name, List<Component> tooltip) {
        this.tier = tier;
        this.setTier = setTier;
        this.min = min;
        this.max = max;
        this.name = name;
        this.tooltip = tooltip;
        int widest = 0;
        var font = Minecraft.getInstance().font;
        for (int t = min; t <= max; t++) widest = Math.max(widest, font.width(name.apply(t)) + font.width("*"));
        int width = widest + ARROW_WIDTH + ARROW_GAP + 1;
        layout(l -> l.size(width, RecipeSpecPanel.ROW_HEIGHT));
    }

    private int hoveredSide(int mouseX, int mouseY) {
        if (!isMouseOverElement(mouseX, mouseY)) return 0;
        return mouseY < getPositionY() + getSizeHeight() / 2 ? 1 : -1;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
        int current = tier.getAsInt();
        var font = Minecraft.getInstance().font;
        var text = name.apply(current);
        var color = text.getStyle().getColor();
        int rgb = color == null ? UITheme.TEXT : 0xFF000000 | UITheme.lightBackgroundColor(color.getValue());
        var plain = Component.literal(text.getString()).withStyle(style -> style.withBold(text.getStyle().isBold()));
        int textLeft = x + w - font.width(plain);
        int arrowX = textLeft - ARROW_GAP - ARROW_WIDTH;
        int side = hoveredSide(mouseX, mouseY);
        if (side != 0) graphics.fill(arrowX - 1, y, x + w, y + h, UITheme.SEGMENT_HOVER);
        int cy = y + h / 2;
        if (current < max) drawArrow(graphics, arrowX, cy - 1, true, side > 0 ? ARROW_HOVER : ARROW_COLOR);
        if (current > min) drawArrow(graphics, arrowX, cy + 1, false, side < 0 ? ARROW_HOVER : ARROW_COLOR);
        graphics.drawString(font, plain, textLeft, y + (h - 8) / 2, rgb, false);
    }

    @OnlyIn(Dist.CLIENT)
    private static void drawArrow(GuiGraphics graphics, int x, int edge, boolean up, int color) {
        for (int i = 0; i < 3; i++) {
            int row = up ? edge - i : edge + i;
            graphics.fill(x + i, row, x + ARROW_WIDTH - i, row + 1, color);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
        gui.getModularUIGui().setHoverTooltip(tooltip, ItemStack.EMPTY, null, null);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOverElement(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
        step(mouseY < getPositionY() + getSizeHeight() / 2.0 ? 1 : -1);
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseWheelMove(double mouseX, double mouseY, double wheelDelta) {
        if (!isMouseOverElement(mouseX, mouseY) || wheelDelta == 0) return super.mouseWheelMove(mouseX, mouseY, wheelDelta);
        step(wheelDelta > 0 ? 1 : -1);
        return true;
    }

    private void step(int delta) {
        int next = Math.clamp(tier.getAsInt() + delta, min, max);
        if (next == tier.getAsInt()) return;
        playButtonClickSound();
        setTier.accept(next);
    }
}
