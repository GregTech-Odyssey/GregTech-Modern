package com.gregtechceu.gtceu.api.gui.fancy;

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

    default boolean coversReason(@Nullable String reasonKey) {
        return false;
    }

    @Nullable
    default String coveredReason() {
        return null;
    }

    static IFancyTooltip covering(String reasonKey, IFancyTooltip tooltip) {
        return new Covering(reasonKey, tooltip);
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

    record Covering(String reasonKey, IFancyTooltip tooltip) implements IFancyTooltip {

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
        public boolean coversReason(@Nullable String reasonKey) {
            return this.reasonKey.equals(reasonKey) || tooltip.coversReason(reasonKey);
        }

        @Override
        public String coveredReason() {
            return reasonKey;
        }
    }
}
