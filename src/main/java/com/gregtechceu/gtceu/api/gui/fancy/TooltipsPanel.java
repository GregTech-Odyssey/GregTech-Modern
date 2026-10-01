package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Getter
public class TooltipsPanel extends Widget {

    protected List<IFancyTooltip> tooltips = new ArrayList<>();
    @Nullable
    @Getter(AccessLevel.NONE)
    private IssueSyncWidget issues;
    @Getter(AccessLevel.NONE)
    private final Map<RecipeLogic, IssueTooltip> issueTooltips = new IdentityHashMap<>(2);

    public TooltipsPanel() {
        super(202, 2, 20, 0);
    }

    public void clear() {
        tooltips.clear();
    }

    public void setIssues(@Nullable IssueSyncWidget issues) {
        this.issues = issues;
        issueTooltips.clear();
    }

    public void attachTooltips(IFancyTooltip... tooltips) {
        for (var tooltip : tooltips) add(resolve(tooltip));
    }

    public void attachRecipeLogics(MetaMachine machine) {
        for (var logic : IssueSyncWidget.logicsOf(machine)) add(resolve(logic));
    }

    private void add(IFancyTooltip tooltip) {
        if (tooltip instanceof IssueTooltip && tooltips.contains(tooltip)) return;
        tooltips.add(tooltip);
    }

    private IFancyTooltip resolve(IFancyTooltip tooltip) {
        if (issues == null || !(tooltip instanceof RecipeLogic logic)) return tooltip;
        var cached = issueTooltips.get(logic);
        if (cached != null) return cached;
        var source = issues.source(logic);
        if (source == null) return tooltip;
        var created = new IssueTooltip(source, this);
        issueTooltips.put(logic, created);
        return created;
    }

    public boolean isShown(int index) {
        var tooltip = tooltips.get(index);
        if (!tooltip.showFancyTooltip()) return false;
        var type = tooltip.coveredIssue();
        if (type == null) return true;
        for (int i = 0; i < index; i++) {
            var other = tooltips.get(i);
            if (other.coveredIssue() == type && other.showFancyTooltip()) return false;
        }
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        var position = getPosition();
        var size = getSize();
        int offsetY = 0;
        for (int i = 0; i < tooltips.size(); i++) {
            var tooltip = tooltips.get(i);
            if (isShown(i)) {
                tooltip.getFancyTooltipIcon().draw(graphics, mouseX, mouseY, position.x, position.y + offsetY, size.width, size.width);
                offsetY += size.getWidth() + 2;
            }
        }
        setSize(new Size(getSize().width, Math.max(0, offsetY)));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (getHoverElement(mouseX, mouseY) == this && gui != null && gui.getModularUIGui() != null) {
            var position = getPosition();
            var size = getSize();
            int offsetY = 0;
            for (int i = 0; i < tooltips.size(); i++) {
                var tooltip = tooltips.get(i);
                if (isShown(i)) {
                    if (isMouseOver(position.x, position.y + offsetY, size.width, size.width, mouseX, mouseY)) {
                        gui.getModularUIGui().setHoverTooltip(tooltip.getFancyTooltip(), ItemStack.EMPTY, null, tooltip.getFancyComponent());
                        return;
                    }
                    offsetY += size.getWidth() + 2;
                }
            }
        }
    }
}
