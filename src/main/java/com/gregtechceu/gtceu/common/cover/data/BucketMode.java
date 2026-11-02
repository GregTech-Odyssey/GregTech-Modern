package com.gregtechceu.gtceu.common.cover.data;

import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.network.chat.Component;

import lombok.Getter;

public enum BucketMode implements EnumSelectorWidget.SelectableEnum {

    BUCKET("cover.bucket.mode.bucket", WidgetIcons.BUCKET, 1000),
    MILLI_BUCKET("cover.bucket.mode.milli_bucket", WidgetIcons.MILLI_BUCKET, 1);

    @Getter
    public final String tooltip;
    public final Component name;
    @Getter
    public final IGuiTexture icon;
    public final int multiplier;

    BucketMode(String tooltip, IGuiTexture icon, int multiplier) {
        this.tooltip = tooltip;
        this.name = Component.translatable(tooltip);
        this.icon = icon;
        this.multiplier = multiplier;
    }
}
