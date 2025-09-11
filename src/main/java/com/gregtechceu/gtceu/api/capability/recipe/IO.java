package com.gregtechceu.gtceu.api.capability.recipe;

import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

import org.jetbrains.annotations.NotNull;

/**
 * The capability can be input or output or both
 */
public enum IO implements EnumSelectorWidget.SelectableEnum {

    IN("gtceu.io.import", "import"),
    OUT("gtceu.io.export", "export"),
    BOTH("gtceu.io.both", "both") {

        @Override
        public boolean support(IO io) {
            return true;
        }
    },
    NONE("gtceu.io.none", "none") {

        @Override
        public boolean support(IO io) {
            return false;
        }
    };

    public final String tooltip;
    public final IGuiTexture icon;

    IO(String tooltip, String textureName) {
        this.tooltip = tooltip;
        this.icon = new ResourceTexture("gtceu:textures/gui/icon/io_mode/" + textureName + ".png");
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
