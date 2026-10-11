package com.gregtechceu.gtceu.api.tooltip;

public enum TipMarker {

    NONE(""),
    DOT("· "),
    CHEVRON("› "),
    PLUS("+ "),
    MINUS("- "),
    CHECK("✔ "),
    CROSS("✘ "),
    WARN("! "),
    KEY("· "),
    MOUSE_LEFT("· "),
    MOUSE_RIGHT("· "),
    MOUSE_MIDDLE("· ");

    public final String fallback;

    TipMarker(String fallback) {
        this.fallback = fallback;
    }
}
