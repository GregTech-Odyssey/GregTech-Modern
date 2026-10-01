package com.gregtechceu.gtceu.uipro;

import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

public enum Level {

    NORMAL,
    GOOD,
    WARNING,
    ERROR;

    private static final Level[] VALUES = values();

    public static Level of(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NORMAL;
    }

    public static Level worst(Level a, Level b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    public boolean hasLamp() {
        return this != NORMAL;
    }

    public int getTextColor() {
        return switch (this) {
            case NORMAL -> UITheme.TEXT;
            case GOOD -> UITheme.STATUS_TEXT_GOOD;
            case WARNING -> UITheme.STATUS_TEXT_WARNING;
            case ERROR -> UITheme.STATUS_TEXT_ERROR;
        };
    }

    public int getScreenColor() {
        return switch (this) {
            case NORMAL -> UITheme.SCREEN_TEXT;
            case GOOD -> UITheme.SCREEN_GOOD;
            case WARNING -> UITheme.SCREEN_WARNING;
            case ERROR -> UITheme.SCREEN_ERROR;
        };
    }

    public int getLampColor() {
        return switch (this) {
            case NORMAL -> 0;
            case GOOD -> UITheme.STATUS_ONLINE;
            case WARNING -> UITheme.STATUS_WARNING;
            case ERROR -> UITheme.STATUS_OFFLINE;
        };
    }
}
