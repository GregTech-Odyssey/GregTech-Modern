package com.gregtechceu.gtceu.common.cover.data;

import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

public enum DistributionMode implements EnumSelectorWidget.SelectableEnum {

    ROUND_ROBIN_GLOBAL("round_robin_global"),
    ROUND_ROBIN_PRIO("round_robin_prio"),
    INSERT_FIRST("insert_first");

    public static final DistributionMode[] VALUES = values();
    private static final float OFFSET = 1.0f / VALUES.length;

    public final String localeName;

    DistributionMode(String localeName) {
        this.localeName = localeName;
    }

    @Override
    public String getTooltip() {
        return "cover.conveyor.distribution." + localeName;
    }

    @Override
    public IGuiTexture getIcon() {
        return switch (this) {
            case ROUND_ROBIN_GLOBAL -> WidgetIcons.DISTRIBUTION_ROUND_ROBIN;
            case ROUND_ROBIN_PRIO -> WidgetIcons.DISTRIBUTION_ROUND_ROBIN_PRIORITY;
            case INSERT_FIRST -> WidgetIcons.DISTRIBUTION_INSERT_FIRST;
        };
    }
}
