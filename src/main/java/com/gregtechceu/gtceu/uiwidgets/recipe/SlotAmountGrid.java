package com.gregtechceu.gtceu.uiwidgets.recipe;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.vfyjxf.taffy.style.AlignContent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SlotAmountGrid extends UIElement {

    public static final int COLUMNS = 2;
    private static final int ACCENT_WIDTH = 2;
    private static final int READOUT_INSET = 3;
    private static final int TEXT_LINE = 8;
    private static final String TIMES = "×";

    public record Entry(Widget slot, Component amount, int accent) {}

    public SlotAmountGrid(int width, List<Entry> entries) {
        int inner = width - 2 * UITheme.PANEL_PADDING;
        int cellWidth = (inner - UISizes.SECTION_GAP) / COLUMNS;
        layout(l -> l.column().width(width).paddingAll(UITheme.PANEL_PADDING).gapAll(UISizes.GAP));
        setBackground(UITheme.PANEL);
        var justify = entries.size() == 1 ? AlignContent.CENTER : AlignContent.START;
        for (int start = 0; start < entries.size(); start += COLUMNS) {
            var row = new UIElement().layout(l -> l.row().height(UISizes.SLOT).gapAll(UISizes.SECTION_GAP).justifyContent(justify));
            for (int i = start; i < Math.min(entries.size(), start + COLUMNS); i++) row.addChild(cell(entries.get(i), cellWidth));
            addChild(row);
        }
    }

    public static int heightFor(int entries) {
        int rows = (entries + COLUMNS - 1) / COLUMNS;
        return 2 * UITheme.PANEL_PADDING + rows * UISizes.SLOT + Math.max(0, rows - 1) * UISizes.GAP;
    }

    private static UIElement cell(Entry entry, int width) {
        var cell = new UIElement().layout(l -> l.row().size(width, UISizes.SLOT).gapAll(UISizes.GAP));
        cell.addChild(entry.slot());
        cell.addChild(new Readout(entry.amount(), entry.accent(), width - UISizes.SLOT - UISizes.GAP));
        return cell;
    }

    private static final class Readout extends UIElement {

        private final Component amount;
        private final int accent;
        @Nullable
        private String text;
        private int clipWidth = -1;
        private boolean truncated;

        private Readout(Component amount, int accent, int width) {
            this.amount = amount;
            this.accent = accent;
            layout(l -> l.size(width, UISizes.SLOT));
            setBackground(UITheme.STATUS_PANEL);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
            int x = getPositionX(), y = getPositionY(), width = getSizeWidth();
            graphics.fill(x + READOUT_INSET, y + READOUT_INSET, x + READOUT_INSET + ACCENT_WIDTH, y + UISizes.SLOT - READOUT_INSET, accent);
            var font = Minecraft.getInstance().font;
            int textLeft = x + READOUT_INSET + ACCENT_WIDTH + READOUT_INSET;
            int timesWidth = font.width(TIMES) + 1;
            int available = x + width - READOUT_INSET - textLeft - timesWidth;
            if (available != clipWidth || text == null) {
                clipWidth = available;
                String full = amount.getString();
                truncated = font.width(full) > available;
                text = UITheme.clip(font, full, available);
            }
            int textY = y + (UISizes.SLOT - TEXT_LINE) / 2;
            int right = x + width - READOUT_INSET;
            int textX = right - font.width(text);
            graphics.drawString(font, TIMES, textX - timesWidth, textY, UITheme.TEXT_SECONDARY, false);
            graphics.drawString(font, text, textX, textY, UITheme.TEXT, false);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
            if (!truncated || gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
            gui.getModularUIGui().setHoverTooltip(List.of(amount), ItemStack.EMPTY, null, null);
        }
    }
}
