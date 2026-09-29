package com.gregtechceu.gtceu.uipro.flow;

import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.FluidSlot;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;

import dev.vfyjxf.taffy.style.AlignContent;
import org.jetbrains.annotations.Nullable;

public final class FlowParts {

    public static final Component DASH = Component.literal("—");

    private FlowParts() {}

    public static UIElement header(Widget icon, Component title) {
        return UIElement.row(UISizes.CONTROL_HEIGHT).layout(l -> l.gapAll(UISizes.GAP).alignCenter())
                .addChildren(icon, TextLine.constant(0, title).layout(l -> l.flex(1)));
    }

    public static UIElement header(Widget icon, String titleKey) {
        return header(icon, Component.translatable(titleKey));
    }

    public static UIElement centered(Widget child) {
        return UIElement.row(child.getSizeHeight()).layout(l -> l.justifyContent(AlignContent.CENTER)).addChild(child);
    }

    public static FluidSlot fluidSlot(@Nullable FluidStack stack) {
        var slot = new FluidSlot(stack == null ? null : new CustomFluidTank(stack), 0, false, false);
        slot.setShowAmount(false);
        return slot;
    }
}
