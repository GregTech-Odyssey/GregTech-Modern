package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.uipro.styletemplate.OreSprites;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

public final class CalloutBubble {

    public enum Tone {

        NEUTRAL(OreSprites.WINDOW_FILL, UITheme.TEXT, -1),
        INFO(0xFFD3ECF1, 0xFF1F5F6C, 0),
        WARNING(0xFFF2D98A, UITheme.STATUS_TEXT_WARNING, 1),
        DANGER(0xFFC24D3E, 0xFFFFFFFF, 2);

        public final int fill;
        public final int text;
        private final IGuiTexture frame;
        @Nullable
        private final IGuiTexture icon;

        Tone(int fill, int text, int iconIndex) {
            this.fill = fill;
            this.text = text;
            this.frame = fill == OreSprites.WINDOW_FILL ? UITheme.WINDOW : new OreSprites.Refilled(OreSprites.BORDER_7, fill, 2, 2, 2, 4);
            this.icon = iconIndex < 0 ? null : ICONS.getSubTexture(iconIndex / 3.0, 0, 1 / 3.0, 1);
        }

        @Nullable
        public IGuiTexture icon() {
            return icon;
        }
    }

    private static final ResourceTexture ICONS = new ResourceTexture(GTCEu.id("textures/gui/uipro/callout_icons.png"));
    public static final int NOTCH = UITheme.POPUP_NOTCH;
    public static final int ICON = 8;
    private static final int ICON_GAP = 2;
    private static final int LABEL_PADDING = 4;
    private static final int LABEL_PADDING_TOP = 4;
    private static final int LABEL_PADDING_BOTTOM = 5;
    public static final int LABEL_HEIGHT = LABEL_PADDING_TOP + ICON + LABEL_PADDING_BOTTOM;
    public static final int GLYPH = 16;
    public static final int GLYPH_WIDTH = 2 * LABEL_PADDING + GLYPH;
    public static final int GLYPH_HEIGHT = LABEL_PADDING_TOP + GLYPH + LABEL_PADDING_BOTTOM;

    private CalloutBubble() {}

    @OnlyIn(Dist.CLIENT)
    public static void drawFrame(GuiGraphics graphics, int x, int y, int width, int height, int notchCenterX, Tone tone) {
        tone.frame.draw(graphics, 0, 0, x, y, width, height);
        drawNotch(graphics, notchCenterX, y, tone.fill);
    }

    @OnlyIn(Dist.CLIENT)
    public static void drawNotch(GuiGraphics graphics, int centerX, int panelTop, int fill) {
        for (int k = 0; k < NOTCH; k++) {
            int y = panelTop - NOTCH + k, half = k + 1;
            graphics.fill(centerX - half, y, centerX + half, y + 1, UITheme.WINDOW_OUTLINE);
            if (k > 0) graphics.fill(centerX - half + 1, y, centerX + half - 1, y + 1, fill);
        }
        graphics.fill(centerX - NOTCH + 1, panelTop, centerX + NOTCH - 1, panelTop + 2, fill);
    }

    public static int labelWidth(Font font, String text) {
        return 2 * LABEL_PADDING + ICON + ICON_GAP + font.width(text);
    }

    @OnlyIn(Dist.CLIENT)
    public static void drawLabel(GuiGraphics graphics, Font font, int x, int y, int notchCenterX, Tone tone, String text) {
        int width = labelWidth(font, text);
        drawFrame(graphics, x, y, width, LABEL_HEIGHT, notchCenterX, tone);
        int iconX = x + LABEL_PADDING, rowY = y + LABEL_PADDING_TOP;
        if (tone.icon != null) tone.icon.draw(graphics, 0, 0, iconX, rowY, ICON, ICON);
        graphics.drawString(font, text, iconX + ICON + ICON_GAP, rowY, tone.text, false);
    }

    @OnlyIn(Dist.CLIENT)
    public static void drawGlyph(GuiGraphics graphics, int x, int y, int notchCenterX, Tone tone, IGuiTexture glyph) {
        drawFrame(graphics, x, y, GLYPH_WIDTH, GLYPH_HEIGHT, notchCenterX, tone);
        glyph.draw(graphics, 0, 0, x + LABEL_PADDING, y + LABEL_PADDING_TOP, GLYPH, GLYPH);
    }
}
