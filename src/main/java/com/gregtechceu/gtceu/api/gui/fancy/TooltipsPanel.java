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
    private IdleReasonSyncWidget reasons;
    @Getter(AccessLevel.NONE)
    private final Map<RecipeLogic, IdleReasonTooltip> reasonTooltips = new IdentityHashMap<>(2);

    public TooltipsPanel() {
        super(202, 2, 20, 0);
    }

    public void clear() {
        tooltips.clear();
    }

    public void setReasons(@Nullable IdleReasonSyncWidget reasons) {
        this.reasons = reasons;
        reasonTooltips.clear();
    }

    public void attachTooltips(IFancyTooltip... tooltips) {
        for (var tooltip : tooltips) add(resolve(tooltip));
    }

    public void attachRecipeLogics(MetaMachine machine) {
        for (var logic : IdleReasonSyncWidget.logicsOf(machine)) add(resolve(logic));
    }

    private void add(IFancyTooltip tooltip) {
        if (tooltip instanceof IdleReasonTooltip && tooltips.contains(tooltip)) return;
        tooltips.add(tooltip);
    }

    private IFancyTooltip resolve(IFancyTooltip tooltip) {
        if (reasons == null || !(tooltip instanceof RecipeLogic logic)) return tooltip;
        var cached = reasonTooltips.get(logic);
        if (cached != null) return cached;
        var state = reasons.state(logic);
        if (state == null) return tooltip;
        var created = new IdleReasonTooltip(state, this);
        reasonTooltips.put(logic, created);
        return created;
    }

    public boolean isShown(int index) {
        var tooltip = tooltips.get(index);
        if (!tooltip.showFancyTooltip()) return false;
        var key = tooltip.coveredReason();
        if (key == null) return true;
        for (int i = 0; i < index; i++) {
            var other = tooltips.get(i);
            if (key.equals(other.coveredReason()) && other.showFancyTooltip()) return false;
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
                // draw icon
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
