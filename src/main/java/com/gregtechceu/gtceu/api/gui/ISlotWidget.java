package com.gregtechceu.gtceu.api.gui;

import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.me.common.StackSizeRenderer;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public interface ISlotWidget {

    static void drawBackgroundAEStyle(@NotNull GuiGraphics graphics, ItemStack itemStack, Position pos) {
        // DrawerHelper.drawItemStack(graphics, itemStack, pos.x, pos.y, -1, (String)null);
        var minecraft = Minecraft.getInstance();
        var gStack = Objects.requireNonNull(GenericStack.fromItemStack(itemStack), "Not a valid ItemStack: " + itemStack);
        AEKeyRendering.drawInGui(
                minecraft,
                graphics,
                pos.x,
                pos.y, gStack.what());

        if (gStack.amount() > 0) {
            String amtText = gStack.what().formatAmount(gStack.amount(), AmountFormat.SLOT);
            StackSizeRenderer.renderSizeLabel(graphics, minecraft.font, pos.x, pos.y, amtText, false);
        }
    }

    @FunctionalInterface
    interface AEStylePredicate {

        boolean shouldUseAEStyle(ItemStack stack);
    }

    AEStylePredicate gtm$getAEStylePredicate();

    void gtm$setAEStylePredicate(AEStylePredicate predicate);
}
