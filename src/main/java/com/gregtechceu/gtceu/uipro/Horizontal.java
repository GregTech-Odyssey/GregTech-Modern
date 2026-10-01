package com.gregtechceu.gtceu.uipro;

public enum Horizontal {

    LEFT(0),
    CENTER(0.5f),
    RIGHT(1);

    private final float offset;

    Horizontal(float offset) {
        this.offset = offset;
    }

    public int offsetIn(int available, int content) {
        return this == LEFT ? 0 : Math.round(Math.max(0, available - content) * offset);
    }
}
