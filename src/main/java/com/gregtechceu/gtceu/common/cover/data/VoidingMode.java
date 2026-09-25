package com.gregtechceu.gtceu.common.cover.data;

import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import lombok.Getter;

public enum VoidingMode implements EnumSelectorWidget.SelectableEnum {

    VOID_ANY("cover.voiding.voiding_mode.void_any", WidgetIcons.VOID_ANY, 1),
    VOID_OVERFLOW("cover.voiding.voiding_mode.void_overflow", WidgetIcons.VOID_OVERFLOW, 1024);

    @Getter
    public final String tooltip;
    @Getter
    public final IGuiTexture icon;
    public final int maxStackSize;

    VoidingMode(String tooltip, IGuiTexture icon, int maxStackSize) {
        this.tooltip = tooltip;
        this.maxStackSize = maxStackSize;
        this.icon = icon;
    }
}
