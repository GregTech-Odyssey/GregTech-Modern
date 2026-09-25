package com.gregtechceu.gtceu.common.cover.data;

import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

public enum ManualIOMode implements EnumSelectorWidget.SelectableEnum {

    DISABLED("disabled"),
    FILTERED("filtered"),
    UNFILTERED("unfiltered");

    public static final ManualIOMode[] VALUES = values();

    public final String localeName;

    ManualIOMode(String localeName) {
        this.localeName = localeName;
    }

    @Override
    public String getTooltip() {
        return "cover.universal.manual_import_export.mode." + localeName;
    }

    @Override
    public IGuiTexture getIcon() {
        return switch (this) {
            case DISABLED -> WidgetIcons.MANUAL_IO_DISABLED;
            case FILTERED -> WidgetIcons.MANUAL_IO_FILTERED;
            case UNFILTERED -> WidgetIcons.MANUAL_IO_UNFILTERED;
        };
    }
}
