package com.gregtechceu.gtceu.uipro.render;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class UIPixels {

    private UIPixels() {}

    public static float snap(float value) {
        double scale = Minecraft.getInstance().getWindow().getGuiScale();
        return (float) (Math.round(value * scale) / scale);
    }

    public static float residual(float value) {
        return snap(value) - Math.round(value);
    }

    public static int center(int start, int outer, int inner) {
        return start + (outer - inner) / 2;
    }
}
