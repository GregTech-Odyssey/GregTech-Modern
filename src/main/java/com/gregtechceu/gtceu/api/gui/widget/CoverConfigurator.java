package com.gregtechceu.gtceu.api.gui.widget;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.common.data.GTItems;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CoverConfigurator implements IFancyConfigurator {

    protected final ICoverable coverable;
    // runtime
    @Nullable
    protected final Direction side;
    @Nullable
    protected final CoverBehavior coverBehavior;

    @Override
    public Component getTitle() {
        if (side != null && coverable.getCoverAtSide(side) instanceof IUICover iuiCover) return iuiCover.getCoverTitle();
        return Component.translatable("gtceu.gui.cover_setting.title");
    }

    @Override
    public List<Component> getTooltips() {
        return List.of(Component.translatable("gtceu.gui.cover_setting.title"));
    }

    @Override
    public IGuiTexture getIcon() {
        return new ItemStackTexture(GTItems.ITEM_FILTER.get());
    }

    @Override
    public Widget createConfigurator() {
        if (side != null && coverable.getCoverAtSide(side) instanceof IUICover iuiCover) return iuiCover.createUIWidget();
        return new WidgetGroup(0, 0, 0, 0);
    }

    public CoverConfigurator(final ICoverable coverable, @Nullable final Direction side, @Nullable final CoverBehavior coverBehavior) {
        this.coverable = coverable;
        this.side = side;
        this.coverBehavior = coverBehavior;
    }
}
