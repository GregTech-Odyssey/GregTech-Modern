package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.machine.issue.IssueLines;
import com.gregtechceu.gtceu.api.machine.issue.IssueSnapshot;
import com.gregtechceu.gtceu.uiwidgets.icon.IssueIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

public final class IssueTooltip implements IFancyTooltip {

    private final Supplier<IssueSnapshot> source;
    private final TooltipsPanel panel;
    private final IssueLines.Cache lines = new IssueLines.Cache();

    public IssueTooltip(Supplier<IssueSnapshot> source, TooltipsPanel panel) {
        this.source = source;
        this.panel = panel;
    }

    @Override
    public IGuiTexture getFancyTooltipIcon() {
        return IssueIcons.iconFor(source.get());
    }

    @Override
    public List<Component> getFancyTooltip() {
        return lines.tooltip(source.get());
    }

    @Override
    public boolean showFancyTooltip() {
        var snapshot = source.get();
        if (!IssueLines.visible(snapshot)) return false;
        var issue = IssueLines.shown(snapshot);
        if (issue == null) return true;
        var type = issue.type();
        for (var other : panel.getTooltips()) {
            if (other != this && other.coversIssue(type) && other.showFancyTooltip()) return false;
        }
        return true;
    }
}
