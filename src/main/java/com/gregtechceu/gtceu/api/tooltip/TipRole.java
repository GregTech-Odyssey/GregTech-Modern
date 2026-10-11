package com.gregtechceu.gtceu.api.tooltip;

/**
 * 一行提示的语义角色：决定标记、颜色与缩进，调用方只声明含义。
 */
public record TipRole(String id, TipMarker marker, int bodyColor, int markerColor, int depth) {

    public static final TipRole HEADING = new TipRole("heading", TipMarker.NONE, TipTheme.HEADING, 0, 0);
    public static final TipRole SUMMARY = new TipRole("summary", TipMarker.NONE, TipTheme.BODY, 0, 0);
    public static final TipRole STAT = new TipRole("stat", TipMarker.NONE, TipTheme.BODY, 0, 0);
    public static final TipRole LORE = new TipRole("lore", TipMarker.NONE, TipTheme.LORE, 0, 0);
    public static final TipRole PLAIN = new TipRole("plain", TipMarker.NONE, -1, 0, 0);
    public static final TipRole METER = new TipRole("meter", TipMarker.NONE, TipTheme.BODY, 0, 0);
    public static final TipRole FOOTER = new TipRole("footer", TipMarker.NONE, TipTheme.BODY, 0, 0);

    public static final TipRole FEATURE = new TipRole("feature", TipMarker.DOT, TipTheme.BODY, TipTheme.MARK, 1);
    public static final TipRole REQUIREMENT = new TipRole("requirement", TipMarker.CHEVRON, TipTheme.BODY, TipTheme.GOLD, 1);
    public static final TipRole ATTENTION = new TipRole("attention", TipMarker.DOT, TipTheme.ATTENTION, TipTheme.ATTENTION, 1);
    public static final TipRole BONUS = new TipRole("bonus", TipMarker.PLUS, TipTheme.POSITIVE, TipTheme.POSITIVE, 1);
    public static final TipRole PENALTY = new TipRole("penalty", TipMarker.MINUS, TipTheme.NEGATIVE, TipTheme.NEGATIVE, 1);
    public static final TipRole WARNING = new TipRole("warning", TipMarker.WARN, TipTheme.NEGATIVE, TipTheme.NEGATIVE, 1);
    public static final TipRole SUPPORTED = new TipRole("supported", TipMarker.CHECK, TipTheme.BODY, TipTheme.POSITIVE, 1);
    public static final TipRole UNSUPPORTED = new TipRole("unsupported", TipMarker.CROSS, TipTheme.BODY, TipTheme.NEGATIVE, 1);
    public static final TipRole NOTE = new TipRole("note", TipMarker.DOT, TipTheme.NOTE, TipTheme.MARK_DIM, 1);
    public static final TipRole DETAIL = new TipRole("detail", TipMarker.NONE, TipTheme.NOTE, 0, 1);
    public static final TipRole FORMULA = new TipRole("formula", TipMarker.DOT, TipTheme.BODY, TipTheme.MARK_DIM, 1);
    public static final TipRole CONTROL_LEFT = new TipRole("control_left", TipMarker.MOUSE_LEFT, TipTheme.BODY, TipTheme.ACCENT, 1);
    public static final TipRole CONTROL_RIGHT = new TipRole("control_right", TipMarker.MOUSE_RIGHT, TipTheme.BODY, TipTheme.ACCENT, 1);
    public static final TipRole CONTROL_MIDDLE = new TipRole("control_middle", TipMarker.MOUSE_MIDDLE, TipTheme.BODY, TipTheme.ACCENT, 1);
    public static final TipRole CONTROL = new TipRole("control", TipMarker.KEY, TipTheme.BODY, TipTheme.ACCENT, 1);

    public boolean isControl() {
        return marker == TipMarker.MOUSE_LEFT || marker == TipMarker.MOUSE_RIGHT || marker == TipMarker.MOUSE_MIDDLE ||
                marker == TipMarker.KEY;
    }

    public TipRole withDepth(int depth) {
        return depth == this.depth ? this : new TipRole(id, marker, bodyColor, markerColor, depth);
    }
}
