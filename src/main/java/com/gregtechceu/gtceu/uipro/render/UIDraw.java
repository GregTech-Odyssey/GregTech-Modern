package com.gregtechceu.gtceu.uipro.render;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.uipro.animation.UIClock;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * 界面绘制原语：多块纯色拼成的图形批进一次 {@link RenderType#gui()} 提交，批内只有纯色块。
 */
@OnlyIn(Dist.CLIENT)
public final class UIDraw {

    private static final int SELECTION_FILL_ALPHA_MIN = 0x08;
    private static final int SELECTION_FILL_ALPHA_MAX = 0x28;
    private static final long SELECTION_PULSE_MS = 1600;
    private static final int DISABLED_HATCH_SIZE = 16;
    private static final int OPTION_MARK_OFF = 0xFFA0A0A0;
    private static final int OPTION_MARK_ON = 0xFFFFFFFF;
    private static final int[][] OPTION_MARK_CHECK_PIXELS = { { 0, 2 }, { 1, 3 }, { 2, 4 }, { 3, 3 }, { 3, 2 }, { 4, 1 }, { 4, 0 } };
    private static final int[][] RESIZE_GRIP_DOTS = { { 4, 0 }, { 2, 2 }, { 4, 2 }, { 0, 4 }, { 2, 4 }, { 4, 4 } };
    private static final int HIGHLIGHT = 0xFFFFFFFF;
    private static final int LAMP_GLINT = 0x80FFFFFF;
    private static final int INSET_LAMP_GLINT = 0x60FFFFFF;
    private static final int PROGRESS_RANGE_ALPHA = 0x50000000;
    private static final long PROGRESS_BONUS_PULSE_MS = 2000;

    private UIDraw() {}

    static VertexConsumer begin(GuiGraphics graphics) {
        return graphics.bufferSource().getBuffer(RenderType.gui());
    }

    static void end(GuiGraphics graphics) {
        graphics.bufferSource().endBatch(RenderType.gui());
        RenderSystem.enableDepthTest();
    }

    static void quad(VertexConsumer consumer, Matrix4f matrix, int x1, int y1, int x2, int y2, int color) {
        if (x1 < x2) {
            int swap = x1;
            x1 = x2;
            x2 = swap;
        }
        if (y1 < y2) {
            int swap = y1;
            y1 = y2;
            y2 = swap;
        }
        float a = (color >>> 24) / 255f, r = (color >> 16 & 0xFF) / 255f, g = (color >> 8 & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        consumer.vertex(matrix, x1, y1, 0).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x1, y2, 0).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x2, y2, 0).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x2, y1, 0).color(r, g, b, a).endVertex();
    }

    private static void ring(VertexConsumer consumer, Matrix4f matrix, int l, int t, int r, int b, int color, boolean roundCorners) {
        int c = roundCorners ? 1 : 0;
        quad(consumer, matrix, l + c, t, r - c, t + 1, color);
        quad(consumer, matrix, l + c, b - 1, r - c, b, color);
        quad(consumer, matrix, l, t + 1, l + 1, b - 1, color);
        quad(consumer, matrix, r - 1, t + 1, r, b - 1, color);
    }

    public static void fillRect(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + height, color);
    }

    public static void strokeRect(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        var consumer = begin(graphics);
        ring(consumer, graphics.pose().last().pose(), x, y, x + width, y + height, color, false);
        end(graphics);
    }

    public static void strokeRoundRect(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        var consumer = begin(graphics);
        ring(consumer, graphics.pose().last().pose(), x, y, x + width, y + height, color, true);
        end(graphics);
    }

    public static void pixelLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int thickness, int color,
                                 int clipL, int clipT, int clipR, int clipB) {
        PixelLines.draw(graphics, x0, y0, x1, y1, thickness, color, clipL, clipT, clipR, clipB);
    }

    public static void underline(GuiGraphics graphics, int x, int y, int width, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
    }

    public static void bevel(GuiGraphics graphics, int x, int y, int width, int height, int topLeft, int bottomRight, int fill) {
        if (width <= 0 || height <= 0) return;
        int r = x + width, b = y + height;
        var consumer = begin(graphics);
        var matrix = graphics.pose().last().pose();
        quad(consumer, matrix, x, y, r, b, fill);
        quad(consumer, matrix, x, y, r - 1, y + 1, topLeft);
        quad(consumer, matrix, x, y, x + 1, b - 1, topLeft);
        quad(consumer, matrix, x + 1, b - 1, r, b, bottomRight);
        quad(consumer, matrix, r - 1, y + 1, r, b, bottomRight);
        end(graphics);
    }

    public static void selectionFrame(GuiGraphics graphics, int x, int y, int width, int height) {
        selectionFrame(graphics, x, y, width, height, UITheme.SELECTION_COLOR);
    }

    public static void selectionFrame(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        int l = x - 1, t = y - 1, r = x + width + 1, b = y + height + 1;
        double phase = UIClock.phase(SELECTION_PULSE_MS);
        int alpha = (int) (SELECTION_FILL_ALPHA_MIN + (SELECTION_FILL_ALPHA_MAX - SELECTION_FILL_ALPHA_MIN) * (0.5 - 0.5 * Math.cos(phase * 2 * Math.PI)));
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, UILayers.SELECTION);
        var consumer = begin(graphics);
        var matrix = pose.last().pose();
        quad(consumer, matrix, l + 2, t + 2, r - 2, b - 2, alpha << 24 | (color & 0xFFFFFF));
        ring(consumer, matrix, l, t, r, b, UITheme.SELECTION_OUTLINE, true);
        ring(consumer, matrix, l + 1, t + 1, r - 1, b - 1, color, false);
        end(graphics);
        pose.popPose();
    }

    public static void hoverOverlay(GuiGraphics graphics, int x, int y, int width, int height) {
        RenderSystem.colorMask(true, true, true, false);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, UILayers.ITEM_OVERLAY, UITheme.SLOT_HOVER_OVERLAY);
        RenderSystem.colorMask(true, true, true, true);
    }

    public static void disabledHatch(GuiGraphics graphics, int x, int y, int width, int height) {
        int left = x + 1, top = y + 1, right = x + width - 1, bottom = y + height - 1;
        if (right <= left || bottom <= top) return;
        var texture = UITheme.disabledHatch();
        RenderSystem.enableBlend();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, UILayers.DISABLED_HATCH);
        for (int ty = top; ty < bottom; ty += DISABLED_HATCH_SIZE) {
            int h = Math.min(DISABLED_HATCH_SIZE, bottom - ty);
            for (int tx = left; tx < right; tx += DISABLED_HATCH_SIZE) {
                int w = Math.min(DISABLED_HATCH_SIZE, right - tx);
                graphics.blit(texture, tx, ty, 0, 0, w, h, DISABLED_HATCH_SIZE, DISABLED_HATCH_SIZE);
            }
        }
        pose.popPose();
    }

    public static void xeiPhantomMark(GuiGraphics graphics, int x, int y, int width, int height, boolean darkSlot) {
        (darkSlot ? GuiTextures.CONFIG_ARROW : GuiTextures.CONFIG_ARROW_DARK).draw(graphics, 0, 0, x, y, width, height);
    }

    public static void lamp(GuiGraphics graphics, int x, int y, int size, int color) {
        var consumer = begin(graphics);
        var matrix = graphics.pose().last().pose();
        quad(consumer, matrix, x, y, x + size, y + size, UITheme.STATUS_LAMP_OUTLINE);
        quad(consumer, matrix, x + 1, y + 1, x + size - 1, y + size - 1, color);
        quad(consumer, matrix, x + 1, y + 1, x + 2, y + 2, LAMP_GLINT);
        end(graphics);
    }

    public static void insetLamp(GuiGraphics graphics, int x, int y, int size, int color) {
        var consumer = begin(graphics);
        var matrix = graphics.pose().last().pose();
        quad(consumer, matrix, x, y, x + size, y + size, color);
        quad(consumer, matrix, x, y, x + size - 1, y + 1, INSET_LAMP_GLINT);
        quad(consumer, matrix, x, y + 1, x + 1, y + size - 1, INSET_LAMP_GLINT);
        end(graphics);
    }

    public static void optionMark(GuiGraphics graphics, int x, int y, boolean multiple, boolean on) {
        int s = UISizes.OPTION_MARK_SIZE, r = x + s, b = y + s;
        int outline = UITheme.STATUS_LAMP_OUTLINE;
        var consumer = begin(graphics);
        var matrix = graphics.pose().last().pose();
        quad(consumer, matrix, x + 1, y, r - 1, y + 1, outline);
        quad(consumer, matrix, x + 1, b - 1, r - 1, b, outline);
        quad(consumer, matrix, x, y + 1, x + 1, b - 1, outline);
        quad(consumer, matrix, r - 1, y + 1, r, b - 1, outline);
        if (!multiple) {
            quad(consumer, matrix, x + 1, y + 1, r - 1, b - 1, on ? OPTION_MARK_ON : OPTION_MARK_OFF);
        } else {
            quad(consumer, matrix, x + 1, y + 1, r - 1, b - 1, OPTION_MARK_ON);
            if (on) {
                int ix = x + 1, iy = y + 1, c = UITheme.STATUS_TEXT_GOOD;
                for (var pixel : OPTION_MARK_CHECK_PIXELS) {
                    quad(consumer, matrix, ix + pixel[0], iy + pixel[1], ix + pixel[0] + 1, iy + pixel[1] + 1, c);
                }
            }
        }
        end(graphics);
    }

    public static void resizeGrip(GuiGraphics graphics, int right, int bottom, boolean active, boolean locked) {
        int x = right - UISizes.RESIZE_GRIP_SIZE, y = bottom - UISizes.RESIZE_GRIP_SIZE;
        int dark = active ? UITheme.SELECTION_COLOR : UITheme.SELECTION_OUTLINE;
        var consumer = begin(graphics);
        var matrix = graphics.pose().last().pose();
        if (locked) {
            quad(consumer, matrix, right - 2, y, right, bottom, dark);
            quad(consumer, matrix, x, bottom - 2, right, bottom, dark);
            quad(consumer, matrix, right - 3, y + 1, right - 2, bottom - 2, HIGHLIGHT);
            quad(consumer, matrix, x + 1, bottom - 3, right - 2, bottom - 2, HIGHLIGHT);
        } else {
            for (var dot : RESIZE_GRIP_DOTS) {
                quad(consumer, matrix, x + dot[0], y + dot[1], x + dot[0] + 1, y + dot[1] + 1, dark);
                quad(consumer, matrix, x + dot[0] + 1, y + dot[1] + 1, x + dot[0] + 2, y + dot[1] + 2, HIGHLIGHT);
            }
        }
        end(graphics);
    }

    public static void dockPlate(GuiGraphics graphics, int x, int y, int width, int height, int accent) {
        int r = x + width, b = y + height;
        var consumer = begin(graphics);
        var matrix = graphics.pose().last().pose();
        quad(consumer, matrix, x + 1, y + 1, r - 1, b - 1, UITheme.WINDOW_FILL);
        if (accent != 0) {
            ring(consumer, matrix, x, y, r, b, accent, true);
        } else {
            ring(consumer, matrix, x, y, r, b, UITheme.DOCK_OUTLINE, true);
            quad(consumer, matrix, x + 1, y + 1, r - 1, y + 2, UITheme.DOCK_HIGHLIGHT);
            quad(consumer, matrix, x + 1, y + 2, x + 2, b - 1, UITheme.DOCK_HIGHLIGHT);
        }
        end(graphics);
    }

    public static void deckPlate(GuiGraphics graphics, int x, int y, int width, int height, int outline, int light, int dark, int fill) {
        if (width < 8 || height < 8) return;
        int r = x + width, b = y + height;
        int mid = average(fill, dark);
        var consumer = begin(graphics);
        var matrix = graphics.pose().last().pose();
        ring(consumer, matrix, x, y, r, b, outline, false);
        quad(consumer, matrix, x + 1, y + 1, r - 1, b - 1, fill);
        quad(consumer, matrix, x + 1, y + 1, r - 2, y + 2, light);
        quad(consumer, matrix, x + 1, y + 1, x + 2, b - 2, light);
        quad(consumer, matrix, x + 2, b - 2, r - 1, b - 1, dark);
        quad(consumer, matrix, r - 2, y + 2, r - 1, b - 1, dark);
        rivet(consumer, matrix, x + 3, y + 3, outline, light, dark, mid);
        rivet(consumer, matrix, r - 6, y + 3, outline, light, dark, mid);
        rivet(consumer, matrix, x + 3, b - 6, outline, light, dark, mid);
        rivet(consumer, matrix, r - 6, b - 6, outline, light, dark, mid);
        end(graphics);
    }

    private static void rivet(VertexConsumer consumer, Matrix4f matrix, int x, int y, int outline, int light, int dark, int mid) {
        quad(consumer, matrix, x, y, x + 3, y + 3, dark);
        quad(consumer, matrix, x, y, x + 2, y + 1, light);
        quad(consumer, matrix, x, y + 1, x + 1, y + 2, light);
        quad(consumer, matrix, x + 1, y + 1, x + 2, y + 2, mid);
        quad(consumer, matrix, x + 2, y + 1, x + 3, y + 3, outline);
        quad(consumer, matrix, x + 1, y + 2, x + 2, y + 3, outline);
    }

    private static int average(int a, int b) {
        return 0xFF000000 | ((a >> 16 & 0xFF) + (b >> 16 & 0xFF)) / 2 << 16 | ((a >> 8 & 0xFF) + (b >> 8 & 0xFF)) / 2 << 8 | ((a & 0xFF) + (b & 0xFF)) / 2;
    }

    public static void etchLine(GuiGraphics graphics, int x, int y, int width, int height, int dark, int light) {
        int top = UIPixels.center(y, height, 2);
        fillRect(graphics, x, top, width, 1, dark);
        fillRect(graphics, x, top + 1, width, 1, light);
    }

    public static void flowPlate(GuiGraphics graphics, int x, int y, int width, int height, int outline, int stripLight, int stripMid) {
        int r = x + width, b = y + height;
        var consumer = begin(graphics);
        var matrix = graphics.pose().last().pose();
        quad(consumer, matrix, x + 1, y + 1, r - 1, b - 1, UITheme.FLOW_NODE_FILL);
        ring(consumer, matrix, x, y, r, b, outline, true);
        quad(consumer, matrix, r - 2, y + 1, r - 1, b - 1, UITheme.FLOW_NODE_SHADE);
        quad(consumer, matrix, x + 1, b - 2, r - 2, b - 1, UITheme.FLOW_NODE_SHADE);
        quad(consumer, matrix, x + 1, y + 4, x + 2, b - 2, UITheme.FLOW_NODE_HIGHLIGHT);
        if (stripMid == 0) {
            quad(consumer, matrix, x + 1, y + 1, r - 2, y + 2, UITheme.FLOW_NODE_HIGHLIGHT);
            quad(consumer, matrix, x + 1, y + 2, x + 2, y + 4, UITheme.FLOW_NODE_HIGHLIGHT);
        } else {
            quad(consumer, matrix, x + 1, y + 1, r - 1, y + 2, stripLight);
            quad(consumer, matrix, x + 1, y + 2, r - 1, y + 3, stripMid);
            quad(consumer, matrix, x + 1, y + 3, r - 1, y + 4, UITheme.FLOW_STRIP_EDGE);
        }
        end(graphics);
    }

    public static void progressTrack(GuiGraphics graphics, int x, int y, int width, int height) {
        UITheme.PROGRESS_TRACK.draw(graphics, 0, 0, x, y, width, height);
    }

    public static int progressFill(GuiGraphics graphics, int x, int y, int width, int height, int from, float ratio, int color) {
        int filled = Math.min(width - 2 - from, Math.round((width - 2) * Math.max(0, ratio)));
        if (filled <= 0) return from;
        graphics.fill(x + 1 + from, y + 1, x + 1 + from + filled, y + height - 1, color);
        return from + filled;
    }

    public static void progressBonus(GuiGraphics graphics, int x, int y, int height, int from, int width, int color) {
        double phase = UIClock.phase(PROGRESS_BONUS_PULSE_MS);
        int alpha = (int) (0x60 + 0x40 * (0.5 - 0.5 * Math.cos(phase * 2 * Math.PI)));
        graphics.fill(x + 1 + from, y + 1, x + 1 + from + width, y + height - 1, alpha << 24 | (color & 0xFFFFFF));
    }

    public static void progressRange(GuiGraphics graphics, int x, int y, int width, int height, long from, long to, long total, int color) {
        if (to <= from || total <= 0) return;
        int inner = width - 2;
        int start = (int) Math.round(inner * Math.min(1, Math.max(0, (double) from / total)));
        int stop = (int) Math.round(inner * Math.min(1, Math.max(0, (double) to / total)));
        if (stop <= start) stop = Math.min(inner, start + 1);
        var consumer = begin(graphics);
        var matrix = graphics.pose().last().pose();
        quad(consumer, matrix, x + 1 + start, y + 1, x + 1 + stop, y + height - 1, (color & 0xFFFFFF) | PROGRESS_RANGE_ALPHA);
        quad(consumer, matrix, x + 1 + start, y + 1, x + 2 + start, y + height - 1, color);
        quad(consumer, matrix, x + stop, y + 1, x + 1 + stop, y + height - 1, color);
        end(graphics);
    }

    public static void progressMarker(GuiGraphics graphics, int x, int y, int width, int height, long position, long total, int color) {
        if (total <= 0) return;
        int inner = width - 2;
        int at = (int) Math.round((inner - 1) * Math.min(1, Math.max(0, (double) position / total)));
        int tick = Math.max(1, (height - UIText.GLYPH_HEIGHT) / 2);
        var consumer = begin(graphics);
        var matrix = graphics.pose().last().pose();
        quad(consumer, matrix, x + 1 + at, y, x + 2 + at, y + tick, color);
        quad(consumer, matrix, x + 1 + at, y + height - tick, x + 2 + at, y + height, color);
        end(graphics);
    }
}
