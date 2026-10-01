package com.gregtechceu.gtceu.uipro.styletemplate;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * 按原像素绘制的九宫格显示屏底图：四边各留 {@code edge} 像素原样画，中间按原图平铺补足，不做非整数拉伸；
 * 可在右下角带 GTO 标志。
 */
public final class ScreenSprite implements IGuiTexture {

    private final ResourceLocation location;
    private final int textureWidth, textureHeight, u0, v0, regionWidth, regionHeight, edge;
    private final boolean logo;

    public ScreenSprite(ResourceLocation location, int textureWidth, int textureHeight, int edge, boolean logo) {
        this(location, textureWidth, textureHeight, 0, 0, textureWidth, textureHeight, edge, logo);
    }

    public ScreenSprite(ResourceLocation location, int textureWidth, int textureHeight, int u0, int v0, int regionWidth, int regionHeight, int edge, boolean logo) {
        this.location = location;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.u0 = u0;
        this.v0 = v0;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
        this.edge = edge;
        this.logo = logo;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
        int left = (int) x, top = (int) y;
        RenderSystem.enableBlend();
        int[] cols = spans(width, regionWidth);
        int[] rows = spans(height, regionHeight);
        for (int r = 0; r < rows.length; r += 3) {
            for (int c = 0; c < cols.length; c += 3) {
                graphics.blit(location, left + cols[c], top + rows[r], u0 + cols[c + 1], v0 + rows[r + 1], cols[c + 2], rows[r + 2], textureWidth, textureHeight);
            }
        }
        if (logo) {
            int lx = left + width - UISizes.LOGO_GAP - UISizes.LOGO_WIDTH - edge, ly = top + height - UISizes.LOGO_GAP - UISizes.LOGO_HEIGHT - edge;
            UITheme.SCREEN_LOGO.draw(graphics, mouseX, mouseY, lx, ly, UISizes.LOGO_WIDTH, UISizes.LOGO_HEIGHT);
        }
    }

    private int[] spans(int size, int texture) {
        if (size <= texture) {
            int head = size - edge;
            return new int[] { 0, 0, Math.max(0, head), Math.max(0, head), texture - edge, Math.min(edge, size) };
        }
        int middle = texture - 2 * edge;
        int repeats = (size - 2 * edge + middle - 1) / middle;
        int[] result = new int[(repeats + 2) * 3];
        result[0] = 0;
        result[1] = 0;
        result[2] = edge;
        int at = edge;
        for (int i = 0; i < repeats; i++) {
            int length = Math.min(middle, size - edge - at);
            result[(i + 1) * 3] = at;
            result[(i + 1) * 3 + 1] = edge;
            result[(i + 1) * 3 + 2] = length;
            at += length;
        }
        int last = (repeats + 1) * 3;
        result[last] = size - edge;
        result[last + 1] = texture - edge;
        result[last + 2] = edge;
        return result;
    }
}
