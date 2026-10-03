package com.gregtechceu.gtceu.uipro.flow;

import com.gregtechceu.gtceu.api.transfer.forge.ForgeFluidAdapter;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.uipro.Horizontal;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.FluidSlot;
import com.gregtechceu.gtceu.uipro.elements.TextLine;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;
import com.gregtechceu.gtceu.uipro.styletemplate.WidgetIconAtlas;
import com.gregtechceu.gtceu.uiwidgets.flow.IssueLine;
import com.gregtechceu.gtceu.uiwidgets.flow.IssueView;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;
import net.minecraftforge.fluids.FluidStack;

import dev.vfyjxf.taffy.style.AlignContent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class FlowParts {

    public static final Component DASH = Component.literal("—");

    private FlowParts() {}

    public static UIElement header(Widget icon, Component title) {
        return UIElement.centeredRow(UISizes.CONTROL_HEIGHT)
                .addChildren(icon, TextLine.constant(0, title).layout(l -> l.flex(1)));
    }

    public static UIElement header(Widget icon, String titleKey) {
        return header(icon, Component.translatable(titleKey));
    }

    public static UIElement centered(Widget child) {
        return UIElement.row(child.getSizeHeight()).layout(l -> l.justifyContent(AlignContent.CENTER)).addChild(child);
    }

    public static FluidSlot fluidSlot(@Nullable FluidStack stack) {
        ForgeFluidAdapter handler = null;
        if (stack != null) {
            var tank = KeyInventory.fluids(1, stack.getAmount());
            tank.set(0, Keys.fluid(stack), stack.getAmount());
            handler = new ForgeFluidAdapter(tank);
        }
        var slot = FluidSlot.of(handler, 0, false, false);
        slot.setShowAmount(false);
        return slot;
    }

    public static IGuiTexture energyIcon(FlowNode node) {
        return UITheme.switching(() -> node.getFlowState().isLit(), new WidgetIconAtlas.PixelExact(WidgetIcons.ENERGY_OFF),
                new WidgetIconAtlas.PixelExact(WidgetIcons.ENERGY_ON));
    }

    public static FlowNode slotBody(FlowNode node, Widget icon, TextLine name, Supplier<Component> amount, Supplier<Level> level,
                                    Supplier<IssueView> issue) {
        node.layout(l -> l.justifyContent(AlignContent.CENTER));
        node.addChildren(centered(icon), name.setTextAlign(Horizontal.CENTER),
                TextLine.of(LayoutStyle.AUTO, amount).setTextAlign(Horizontal.CENTER).bindLevel(level),
                new IssueLine(LayoutStyle.AUTO, null, issue));
        return node;
    }
}
