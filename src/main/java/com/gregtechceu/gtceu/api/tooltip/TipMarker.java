package com.gregtechceu.gtceu.api.tooltip;

/**
 * 提示行行首的像素标记种类。
 * <b>仅用于物品/方块 tooltip（悬停提示框），不是通用的文字渲染设施</b>：机器界面、说明页、显示行、聊天一律用普通
 * {@code Component} 与 uipro 文本组件，不要拿这里的行或规格去拼。
 */
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
