package com.gregtechceu.gtceu.uipro.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.joml.Vector4f;

@OnlyIn(Dist.CLIENT)
public final class UIClip {

    private static final Vector4f MIN = new Vector4f();
    private static final Vector4f MAX = new Vector4f();

    private UIClip() {}

    public static void push(GuiGraphics graphics, int x, int y, int width, int height) {
        var matrix = graphics.pose().last().pose();
        matrix.transform(MIN.set(x, y, 0, 1));
        matrix.transform(MAX.set(x + width, y + height, 0, 1));
        graphics.enableScissor(Math.round(MIN.x), Math.round(MIN.y), Math.round(MAX.x), Math.round(MAX.y));
    }

    public static void pop(GuiGraphics graphics) {
        graphics.disableScissor();
    }
}
