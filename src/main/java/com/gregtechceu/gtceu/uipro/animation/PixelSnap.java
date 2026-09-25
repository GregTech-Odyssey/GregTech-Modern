package com.gregtechceu.gtceu.uipro.animation;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public final class PixelSnap {

    private PixelSnap() {}

    @OnlyIn(Dist.CLIENT)
    public static float snap(float value) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        return (float) (Math.round(value * scale) / scale);
    }

    @OnlyIn(Dist.CLIENT)
    public static float residual(float value) {
        return snap(value) - Math.round(value);
    }
}
