package com.gregtechceu.gtceu.uipro.render;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.joml.Matrix4f;

import java.io.IOException;
import java.io.InputStream;

/**
 * 按矩形画独立 PNG 或方块图集精灵：可旋转（中心或左上角支点）、乘色含透明度；画前提交已攒的色块。
 */
@OnlyIn(Dist.CLIENT)
public final class UIImage {

    private static final int PNG_HEADER_BYTES = 24;
    private static final FileInfo MISSING = new FileInfo(false, 0, 0);
    private static final Object2ObjectOpenHashMap<ResourceLocation, FileInfo> FILES = new Object2ObjectOpenHashMap<>();

    private record FileInfo(boolean present, int width, int height) {}

    private UIImage() {}

    public static void draw(GuiGraphics graphics, ImageRef image, float x, float y, float width, float height, int argb) {
        draw(graphics, image, x, y, width, height, 0, false, argb);
    }

    public static void draw(GuiGraphics graphics, ImageRef image, float x, float y, float width, float height,
                            float rotationDeg, boolean pivotCorner, int argb) {
        if (width <= 0 || height <= 0 || (argb >>> 24) == 0) return;
        if (image.isSprite()) {
            var sprite = sprite(image.location());
            submit(graphics, sprite.atlasLocation(), x, y, width, height, rotationDeg, pivotCorner,
                    sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), argb);
        } else if (file(image.location()).present) {
            submit(graphics, image.location(), x, y, width, height, rotationDeg, pivotCorner, 0, 0, 1, 1, argb);
        }
    }

    public static void drawCover(GuiGraphics graphics, ImageRef image, float x, float y, float width, float height, int argb) {
        if (width <= 0 || height <= 0 || (argb >>> 24) == 0) return;
        ResourceLocation texture;
        float u0, v0, u1, v1;
        int imageWidth, imageHeight;
        if (image.isSprite()) {
            var sprite = sprite(image.location());
            texture = sprite.atlasLocation();
            u0 = sprite.getU0();
            v0 = sprite.getV0();
            u1 = sprite.getU1();
            v1 = sprite.getV1();
            imageWidth = sprite.contents().width();
            imageHeight = sprite.contents().height();
        } else {
            var info = file(image.location());
            if (!info.present) return;
            texture = image.location();
            u0 = v0 = 0;
            u1 = v1 = 1;
            imageWidth = info.width;
            imageHeight = info.height;
        }
        if (imageWidth > 0 && imageHeight > 0) {
            float keepU = Math.min(1, width * imageHeight / (height * imageWidth));
            float keepV = Math.min(1, height * imageWidth / (width * imageHeight));
            float cutU = (u1 - u0) * (1 - keepU) / 2, cutV = (v1 - v0) * (1 - keepV) / 2;
            u0 += cutU;
            u1 -= cutU;
            v0 += cutV;
            v1 -= cutV;
        }
        submit(graphics, texture, x, y, width, height, 0, false, u0, v0, u1, v1, argb);
    }

    public static boolean exists(ImageRef image) {
        if (image.isSprite()) return !MissingTextureAtlasSprite.getLocation().equals(sprite(image.location()).contents().name());
        return file(image.location()).present;
    }

    public static int width(ImageRef image) {
        return image.isSprite() ? sprite(image.location()).contents().width() : file(image.location()).width;
    }

    public static int height(ImageRef image) {
        return image.isSprite() ? sprite(image.location()).contents().height() : file(image.location()).height;
    }

    public static void invalidate() {
        FILES.clear();
    }

    private static TextureAtlasSprite sprite(ResourceLocation location) {
        return Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(location);
    }

    private static FileInfo file(ResourceLocation location) {
        var info = FILES.get(location);
        if (info == null) {
            info = load(location);
            FILES.put(location, info);
        }
        return info;
    }

    private static FileInfo load(ResourceLocation location) {
        var resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            GTCEu.LOGGER.warn("UI image {} does not exist", location);
            return MISSING;
        }
        try (InputStream stream = resource.get().open()) {
            byte[] header = stream.readNBytes(PNG_HEADER_BYTES);
            if (header.length == PNG_HEADER_BYTES && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
                return new FileInfo(true, readInt(header, 16), readInt(header, 20));
            }
            return new FileInfo(true, 0, 0);
        } catch (IOException e) {
            GTCEu.LOGGER.warn("UI image {} could not be read", location, e);
            return MISSING;
        }
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 24 | (bytes[offset + 1] & 0xFF) << 16 | (bytes[offset + 2] & 0xFF) << 8 | bytes[offset + 3] & 0xFF;
    }

    private static void submit(GuiGraphics graphics, ResourceLocation texture, float x, float y, float width, float height,
                               float rotationDeg, boolean pivotCorner, float u0, float v0, float u1, float v1, int argb) {
        graphics.flush();
        float pivotX = pivotCorner ? x : x + width / 2, pivotY = pivotCorner ? y : y + height / 2;
        float left = x - pivotX, top = y - pivotY, right = left + width, bottom = top + height;
        float cos = 1, sin = 0;
        if (rotationDeg != 0) {
            double radians = Math.toRadians(rotationDeg);
            cos = (float) Math.cos(radians);
            sin = (float) Math.sin(radians);
        }
        int a = argb >>> 24, r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        vertex(builder, matrix, pivotX, pivotY, left, top, cos, sin, u0, v0, r, g, b, a);
        vertex(builder, matrix, pivotX, pivotY, left, bottom, cos, sin, u0, v1, r, g, b, a);
        vertex(builder, matrix, pivotX, pivotY, right, bottom, cos, sin, u1, v1, r, g, b, a);
        vertex(builder, matrix, pivotX, pivotY, right, top, cos, sin, u1, v0, r, g, b, a);
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, float pivotX, float pivotY, float dx, float dy,
                               float cos, float sin, float u, float v, int r, int g, int b, int a) {
        builder.vertex(matrix, pivotX + dx * cos - dy * sin, pivotY + dx * sin + dy * cos, 0).color(r, g, b, a).uv(u, v).endVertex();
    }
}
