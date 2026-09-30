package com.gregtechceu.gtceu.uipro.view;

import com.gregtechceu.gtceu.uipro.UIElement;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public final class DragGesture {

    public static final double THRESHOLD = 3;

    private final UIElement owner;
    private int button = -1;
    private double pressX, pressY;
    private double lastX, lastY;
    private float startX, startY;
    private boolean dragging;

    public DragGesture(UIElement owner) {
        this.owner = owner;
    }

    @OnlyIn(Dist.CLIENT)
    public void press(int button, double mouseX, double mouseY, float startX, float startY) {
        this.button = button;
        this.pressX = this.lastX = mouseX;
        this.pressY = this.lastY = mouseY;
        this.startX = startX;
        this.startY = startY;
        this.dragging = false;
        owner.capturePointer(button);
    }

    public boolean isPressed() {
        return button >= 0;
    }

    public int button() {
        return button;
    }

    public boolean isDragging() {
        return dragging;
    }

    public boolean drag(double mouseX, double mouseY) {
        if (!dragging && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > THRESHOLD) {
            dragging = true;
            lastX = pressX;
            lastY = pressY;
        }
        return dragging;
    }

    public double stepX(double mouseX) {
        double delta = mouseX - lastX;
        lastX = mouseX;
        return delta;
    }

    public double stepY(double mouseY) {
        double delta = mouseY - lastY;
        lastY = mouseY;
        return delta;
    }

    public float panX(double mouseX, float scale) {
        return startX - (float) (mouseX - pressX) / scale;
    }

    public float panY(double mouseY, float scale) {
        return startY - (float) (mouseY - pressY) / scale;
    }

    @OnlyIn(Dist.CLIENT)
    public boolean release() {
        boolean click = button >= 0 && !dragging;
        button = -1;
        dragging = false;
        owner.releasePointer();
        return click;
    }
}
