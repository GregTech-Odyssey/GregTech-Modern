package com.gregtechceu.gtceu.uipro.render;

import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class UIText {

    public static final int GLYPH_HEIGHT = 8;
    public static final String ELLIPSIS = "…";
    private static final String[] UNITS = { "k", "M", "G", "T", "P", "E" };
    private static int generation;

    private UIText() {}

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    public static int generation() {
        return generation;
    }

    public static void invalidate() {
        generation++;
    }

    public static int centerY(int top, int height) {
        return top + (height - GLYPH_HEIGHT) / 2;
    }

    public static int width(String text) {
        return font().width(text);
    }

    public static int width(FormattedText text) {
        return font().width(text);
    }

    public static String fit(String text, int maxWidth) {
        var font = font();
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ELLIPSIS))) + ELLIPSIS;
    }

    public static void drawLeft(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawString(font(), text, x, y, color, false);
    }

    public static void drawLeft(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(font(), text, x, y, color, false);
    }

    public static void drawRight(GuiGraphics graphics, String text, int right, int y, int color) {
        var font = font();
        graphics.drawString(font, text, right - font.width(text), y, color, false);
    }

    public static void drawCentered(GuiGraphics graphics, String text, int centerX, int y, int maxWidth, int color) {
        var font = font();
        var fitted = fit(text, maxWidth);
        graphics.drawString(font, fitted, centerX - font.width(fitted) / 2, y, color, false);
    }

    public static void drawLabelValue(GuiGraphics graphics, int x, int y, int width, int height, String label, String value, int valueColor) {
        var font = font();
        int textY = centerY(y, height);
        int valueWidth = Math.min(font.width(value), (width - 2 * UISizes.TEXT_PADDING) / 2);
        String shownValue = fit(value, valueWidth);
        graphics.drawString(font, shownValue, x + width - UISizes.TEXT_PADDING + 1 - font.width(shownValue), textY, valueColor, false);
        String shownLabel = fit(label, width - 3 * UISizes.TEXT_PADDING - font.width(shownValue));
        graphics.drawString(font, shownLabel, x + UISizes.TEXT_PADDING - 1, textY, UITheme.TEXT, false);
    }

    public static void drawItemCount(GuiGraphics graphics, String text, int itemX, int itemY) {
        var font = font();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, UILayers.ITEM_OVERLAY);
        graphics.drawString(font, text, itemX + 17 - font.width(text), itemY + 9, 0xFFFFFFFF, true);
        pose.popPose();
    }

    public static String formatCompact(long value, int maxWidth) {
        if (value < 0) return "-" + formatCompact(value == Long.MIN_VALUE ? Long.MAX_VALUE : -value, maxWidth - width("-"));
        String plain = Long.toString(value);
        if (value < 1000 || width(plain) <= maxWidth) return plain;
        int unit = 0;
        long divisor = 1000;
        while (unit < UNITS.length - 1 && value / divisor >= 1000) {
            divisor *= 1000;
            unit++;
        }
        long whole = value / divisor;
        String suffix = UNITS[unit];
        if (whole < 10) {
            long tenth = value % divisor / (divisor / 10);
            if (tenth > 0) {
                String decimal = whole + "." + tenth + suffix;
                if (width(decimal) <= maxWidth) return decimal;
            }
        }
        String integer = whole + suffix;
        if (whole < 100 || width(integer) <= maxWidth || unit == UNITS.length - 1) return integer;
        return "." + whole / 100 + UNITS[unit + 1];
    }
}
