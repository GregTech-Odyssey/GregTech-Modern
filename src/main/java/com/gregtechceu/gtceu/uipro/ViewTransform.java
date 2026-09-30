package com.gregtechceu.gtceu.uipro;

import net.minecraft.client.renderer.Rect2i;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.PoseStack;

public final class ViewTransform {

    private float translateX, translateY;
    private float scale = 1;

    public void set(float translateX, float translateY, float scale) {
        this.translateX = translateX;
        this.translateY = translateY;
        this.scale = scale;
    }

    public float translateX() {
        return translateX;
    }

    public float translateY() {
        return translateY;
    }

    public float scale() {
        return scale;
    }

    public boolean isIdentity() {
        return translateX == 0 && translateY == 0 && scale == 1;
    }

    public double toLocalX(double x, int pivotX) {
        return pivotX + (x - pivotX - translateX) / scale;
    }

    public double toLocalY(double y, int pivotY) {
        return pivotY + (y - pivotY - translateY) / scale;
    }

    public double toParentX(double x, int pivotX) {
        return pivotX + (x - pivotX) * scale + translateX;
    }

    public double toParentY(double y, int pivotY) {
        return pivotY + (y - pivotY) * scale + translateY;
    }

    @OnlyIn(Dist.CLIENT)
    public Rect2i toParent(Rect2i rect, int pivotX, int pivotY) {
        int x = (int) Math.floor(toParentX(rect.getX(), pivotX));
        int y = (int) Math.floor(toParentY(rect.getY(), pivotY));
        return new Rect2i(x, y, Math.round(rect.getWidth() * scale), Math.round(rect.getHeight() * scale));
    }

    @OnlyIn(Dist.CLIENT)
    public void apply(PoseStack pose, int pivotX, int pivotY) {
        pose.translate(pivotX + translateX, pivotY + translateY, 0);
        pose.scale(scale, scale, 1);
        pose.translate(-pivotX, -pivotY, 0);
    }
}
