package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

import org.jetbrains.annotations.NotNull;

/**
 * The capability can be input or output or both
 */
public enum IO implements EnumSelectorWidget.SelectableEnum {

    IN("gtceu.io.import", WidgetIcons.COVER_IMPORT),
    OUT("gtceu.io.export", WidgetIcons.COVER_EXPORT),
    BOTH("gtceu.io.both", legacyIcon("both")) {

        @Override
        public boolean support(IO io) {
            return true;
        }
    },
    NONE("gtceu.io.none", legacyIcon("none")) {

        @Override
        public boolean support(IO io) {
            return false;
        }
    };

    public final String tooltip;
    public final IGuiTexture icon;

    IO(String tooltip, IGuiTexture icon) {
        this.tooltip = tooltip;
        this.icon = icon;
    }

    private static IGuiTexture legacyIcon(String textureName) {
        return new ResourceTexture("gtceu:textures/gui/icon/io_mode/" + textureName + ".png");
    }

    public boolean support(IO io) {
        return this == io;
    }

    public @NotNull String getTooltip() {
        return this.tooltip;
    }

    public @NotNull IGuiTexture getIcon() {
        return this.icon;
    }
}
