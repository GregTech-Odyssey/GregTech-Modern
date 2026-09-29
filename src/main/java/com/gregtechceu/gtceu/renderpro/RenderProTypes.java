package com.gregtechceu.gtceu.renderpro;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.platform.NativeImage;

@OnlyIn(Dist.CLIENT)
public final class RenderProTypes {

    public static final ResourceLocation GLOW_TEXTURE = GTCEu.id("renderpro/glow");
    public static final ResourceLocation WHITE_TEXTURE = GTCEu.id("renderpro/white");
    private static final int GLOW_SIZE = 16;
    private static final int GLOW_STEPS = 5;
    private static boolean texturesReady;

    private RenderProTypes() {}

    public static RenderType voxels() {
        return RenderType.entitySolid(WHITE_TEXTURE);
    }

    public static RenderType glows() {
        return RenderType.eyes(GLOW_TEXTURE);
    }

    public static void ensureTextures() {
        if (texturesReady) return;
        texturesReady = true;
        var glow = new NativeImage(GLOW_SIZE, GLOW_SIZE, false);
        float center = (GLOW_SIZE - 1) / 2F;
        for (int x = 0; x < GLOW_SIZE; x++) {
            for (int y = 0; y < GLOW_SIZE; y++) {
                float dx = (x - center) / (GLOW_SIZE / 2F), dy = (y - center) / (GLOW_SIZE / 2F);
                float falloff = Math.max(0, 1 - (float) Math.sqrt(dx * dx + dy * dy));
                float stepped = (float) Math.ceil(falloff * falloff * GLOW_STEPS) / GLOW_STEPS;
                glow.setPixelRGBA(x, y, Math.round(stepped * 255) << 24 | 0xFFFFFF);
            }
        }
        var white = new NativeImage(1, 1, false);
        white.setPixelRGBA(0, 0, 0xFFFFFFFF);
        var textures = Minecraft.getInstance().getTextureManager();
        textures.register(GLOW_TEXTURE, new DynamicTexture(glow));
        textures.register(WHITE_TEXTURE, new DynamicTexture(white));
    }
}
