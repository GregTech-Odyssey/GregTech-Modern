package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.uiwidgets.icon.IdleReasonIcons;
import com.gregtechceu.gtceu.uiwidgets.icon.IdleReasonInfo;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class IdleReasonTooltip implements IFancyTooltip {

    private final IdleReasonSyncWidget.State state;
    private final TooltipsPanel panel;
    private int cachedStatus = -1;
    @Nullable
    private Component cachedReason;
    private List<Component> lines = Collections.emptyList();

    public IdleReasonTooltip(IdleReasonSyncWidget.State state, TooltipsPanel panel) {
        this.state = state;
        this.panel = panel;
    }

    @Override
    public IGuiTexture getFancyTooltipIcon() {
        return IdleReasonIcons.iconFor(state.getStatus(), state.getReason());
    }

    @Override
    public List<Component> getFancyTooltip() {
        int status = state.getStatus();
        var reason = state.getReason();
        if (status != cachedStatus || reason != cachedReason) {
            cachedStatus = status;
            cachedReason = reason;
            lines = IdleReasonInfo.tooltip(status, reason);
        }
        return lines;
    }

    @Override
    public boolean showFancyTooltip() {
        if (!IdleReasonInfo.visible(state.getStatus()) || !state.isAvailable()) return false;
        var key = IdleReasonInfo.keyOf(state.getReason());
        if (key == null) return true;
        for (var other : panel.getTooltips()) {
            if (other != this && other.coversReason(key) && other.showFancyTooltip()) return false;
        }
        return true;
    }
}
