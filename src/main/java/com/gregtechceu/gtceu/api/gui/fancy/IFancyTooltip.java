package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.machine.issue.IssueType;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public interface IFancyTooltip {

    IGuiTexture getFancyTooltipIcon();

    List<Component> getFancyTooltip();

    default boolean showFancyTooltip() {
        return true;
    }

    @Nullable
    default TooltipComponent getFancyComponent() {
        return null;
    }

    default boolean coversIssue(IssueType type) {
        return false;
    }

    @Nullable
    default IssueType coveredIssue() {
        return null;
    }

    static IFancyTooltip covering(IssueType type, IFancyTooltip tooltip) {
        return new Covering(type, tooltip);
    }

    record Basic(Supplier<IGuiTexture> icon, Supplier<List<Component>> content, BooleanSupplier predicate,
                 Supplier<TooltipComponent> componentSupplier)
            implements IFancyTooltip {

        @Override
        public IGuiTexture getFancyTooltipIcon() {
            return icon.get();
        }

        @Override
        public List<Component> getFancyTooltip() {
            return content.get();
        }

        @Override
        public @Nullable TooltipComponent getFancyComponent() {
            return componentSupplier.get();
        }

        @Override
        public boolean showFancyTooltip() {
            return predicate.getAsBoolean();
        }
    }

    record Covering(IssueType type, IFancyTooltip tooltip) implements IFancyTooltip {

        @Override
        public IGuiTexture getFancyTooltipIcon() {
            return tooltip.getFancyTooltipIcon();
        }

        @Override
        public List<Component> getFancyTooltip() {
            return tooltip.getFancyTooltip();
        }

        @Override
        public boolean showFancyTooltip() {
            return tooltip.showFancyTooltip();
        }

        @Override
        public @Nullable TooltipComponent getFancyComponent() {
            return tooltip.getFancyComponent();
        }

        @Override
        public boolean coversIssue(IssueType type) {
            return this.type == type || tooltip.coversIssue(type);
        }

        @Override
        public IssueType coveredIssue() {
            return type;
        }
    }
}
