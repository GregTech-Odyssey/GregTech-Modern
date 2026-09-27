package com.gregtechceu.gtceu.uipro.canvas;

public final class PanGesture {

    private static final double THRESHOLD = 3;

    private int button = -1;
    private double pressX, pressY;
    private float startX, startY;
    private boolean panning;

    public void press(int button, double mouseX, double mouseY, float offsetX, float offsetY) {
        this.button = button;
        this.pressX = mouseX;
        this.pressY = mouseY;
        this.startX = offsetX;
        this.startY = offsetY;
        this.panning = false;
    }

    public boolean isPressed() {
        return button >= 0;
    }

    public int button() {
        return button;
    }

    public boolean isPanning() {
        return panning;
    }

    public boolean drag(double mouseX, double mouseY, boolean allowed) {
        if (!panning && allowed && (button == 0 || button == 2) && Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > THRESHOLD) panning = true;
        return panning;
    }

    public float offsetX(double mouseX, float scale) {
        return startX - (float) (mouseX - pressX) / scale;
    }

    public float offsetY(double mouseY, float scale) {
        return startY - (float) (mouseY - pressY) / scale;
    }

    public void release() {
        button = -1;
        panning = false;
    }
}
