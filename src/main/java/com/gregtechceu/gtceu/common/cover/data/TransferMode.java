package com.gregtechceu.gtceu.common.cover.data;

import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import lombok.Getter;

public enum TransferMode implements EnumSelectorWidget.SelectableEnum {

    TRANSFER_ANY("cover.robotic_arm.transfer_mode.transfer_any", WidgetIcons.TRANSFER_ANY, 1),
    TRANSFER_EXACT("cover.robotic_arm.transfer_mode.transfer_exact", WidgetIcons.TRANSFER_EXACT, 1024),
    KEEP_EXACT("cover.robotic_arm.transfer_mode.keep_exact", WidgetIcons.KEEP_EXACT, 1024);

    @Getter
    public final String tooltip;
    @Getter
    public final IGuiTexture icon;
    public final int maxStackSize;

    TransferMode(String tooltip, IGuiTexture icon, int maxStackSize) {
        this.tooltip = tooltip;
        this.maxStackSize = maxStackSize;
        this.icon = icon;
    }
}
