package com.gregtechceu.gtceu.client.tooltip;

import com.gregtechceu.gtceu.api.tooltip.TipContents;
import com.gregtechceu.gtceu.api.tooltip.TipLineComponent;
import com.gregtechceu.gtceu.api.tooltip.TipMarker;
import com.gregtechceu.gtceu.api.tooltip.TipRole;
import com.gregtechceu.gtceu.api.tooltip.TipTheme;
import com.gregtechceu.gtceu.client.util.DrawUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 提示行的客户端绘制：标记按整像素画在固定列，正文从固定列起排并悬挂缩进折行。
 */
@OnlyIn(Dist.CLIENT)
public final class ClientTipLine extends ClientTextTooltip {

    public static final int LINE_HEIGHT = 10;
    public static final int INDENT = 11;
    private static final int MARKER_X = 2;
    private static final int FULL_BRIGHT = 0xF000F0;

    private static final int MARKER_WIDTH = 7;
    private static final int TEXT_CENTER = 4;

    private static final String[] DOT = { "##", "##" };
    private static final String[] CHEVRON = { "##..", ".##.", "..##", "..##", ".##.", "##.." };
    private static final String[] PLUS = { "..##..", "..##..", "######", "######", "..##..", "..##.." };
    private static final String[] MINUS = { "######", "######" };
    private static final String[] CHECK = { "......#", ".....##", "#...##.", "##.##..", ".###...", "..#...." };
    private static final String[] CROSS = { "##..##", ".####.", "..##..", "..##..", ".####.", "##..##" };
    private static final String[] WARN = { "...#...", "..###..", "..#.#..", ".##.##.", ".##.##.", "#######", "###.###", "#######" };
    private static final String[] KEY = { ".#####.", "#.....#", "#.+++.#", "#.....#", "#######", ".#####." };
    private static final String[] MOUSE_LEFT = { ".##.##.", "#++#..#", "#++#..#", "#######", "#.....#", "#.....#", "#.....#", ".#####." };
    private static final String[] MOUSE_RIGHT = { ".##.##.", "#..#++#", "#..#++#", "#######", "#.....#", "#.....#", "#.....#", ".#####." };
    private static final String[] MOUSE_MIDDLE = { ".##+##.", "#..+..#", "#..+..#", "#######", "#.....#", "#.....#", "#.....#", ".#####." };

    private static final int METER_WIDTH = 120;
    private static final int METER_HEIGHT = 4;

    private final TipRole role;
    @Nullable
    private final TipContents.Meter meter;
    private final int padTop;
    private final int indent;
    private final List<FormattedCharSequence> lines;

    public ClientTipLine(TipLineComponent component) {
        this(component, split(component));
    }

    private ClientTipLine(TipLineComponent component, List<FormattedCharSequence> lines) {
        super(lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.get(0));
        this.role = component.contents().role();
        this.meter = component.contents().meter();
        this.padTop = component.padTop();
        this.indent = role.depth() * INDENT;
        this.lines = lines;
    }

    private static List<FormattedCharSequence> split(TipLineComponent component) {
        Font font = Minecraft.getInstance().font;
        int indent = component.contents().role().depth() * INDENT;
        return font.split(component.contents().styledBody(), Math.max(80, component.maxWidth() - indent));
    }

    @Override
    public int getHeight() {
        if (meter != null) return padTop + Math.max(1, lines.size()) * LINE_HEIGHT + METER_HEIGHT;
        return padTop + Math.max(1, lines.size()) * LINE_HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        int width = 0;
        for (FormattedCharSequence line : lines) width = Math.max(width, font.width(line));
        return meter != null ? Math.max(METER_WIDTH, indent + width) : indent + width;
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
        int ty = y + padTop;
        for (FormattedCharSequence line : lines) {
            font.drawInBatch(line, x + indent, ty, -1, true, matrix, buffer, Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
            ty += LINE_HEIGHT;
        }
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        if (meter != null) {
            int w = METER_WIDTH;
            int by = y + padTop + Math.max(1, lines.size()) * LINE_HEIGHT;
            graphics.fill(x, by, x + w, by + 3, TipTheme.ENERGY_BAR_SHADOW);
            graphics.fill(x, by, x + w - 1, by + 2, TipTheme.ENERGY_BAR_TRACK);
            int from = 0;
            float total = 0;
            for (int i = 0; i < meter.ratios().length; i++) {
                total = Math.min(1f, total + Math.max(0, meter.ratios()[i]));
                int to = Math.round((w - 1) * total);
                if (to > from) {
                    DrawUtil.fillHorizontalGradient(graphics, RenderType.gui(), x + from, by, x + to, by + 2,
                            meter.lefts()[i], meter.rights()[i], 0);
                    from = to;
                }
            }
            return;
        }
        TipMarker marker = role.marker();
        if (marker == TipMarker.NONE) return;
        int mx = x + (role.depth() - 1) * INDENT + MARKER_X;
        int my = y + padTop;
        String[] map = switch (marker) {
            case DOT -> DOT;
            case CHEVRON -> CHEVRON;
            case PLUS -> PLUS;
            case MINUS -> MINUS;
            case CHECK -> CHECK;
            case CROSS -> CROSS;
            case WARN -> WARN;
            case KEY -> KEY;
            case MOUSE_LEFT -> MOUSE_LEFT;
            case MOUSE_RIGHT -> MOUSE_RIGHT;
            case MOUSE_MIDDLE -> MOUSE_MIDDLE;
            default -> null;
        };
        if (map == null) return;
        draw(graphics, map, mx + (MARKER_WIDTH - map[0].length()) / 2, my + TEXT_CENTER - map.length / 2);
    }

    private void draw(GuiGraphics graphics, String[] map, int ox, int oy) {
        boolean mouse = role.isControl();
        int main = 0xFF000000 | (mouse ? TipTheme.MOUSE : role.markerColor());
        int accent = 0xFF000000 | role.markerColor();
        for (int pass = 0; pass < 2; pass++) {
            for (int r = 0; r < map.length; r++) {
                String row = map[r];
                for (int c = 0; c < row.length(); c++) {
                    char ch = row.charAt(c);
                    if (ch == '.') continue;
                    int color = ch == '+' ? accent : main;
                    if (pass == 0) {
                        graphics.fill(ox + c + 1, oy + r + 1, ox + c + 2, oy + r + 2, shadow(color));
                    } else {
                        graphics.fill(ox + c, oy + r, ox + c + 1, oy + r + 1, color);
                    }
                }
            }
        }
    }

    private static int shadow(int color) {
        int r = (color >> 16 & 0xFF) / 4;
        int g = (color >> 8 & 0xFF) / 4;
        int b = (color & 0xFF) / 4;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
