package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncItem;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class ItemCell extends UIElement {

    public static final int SIZE = UISizes.SLOT;

    private final SyncValue<SyncItem> item;

    public ItemCell(Supplier<ItemStack> stack, ItemStack initial) {
        layout(l -> l.size(SIZE, SIZE));
        this.item = addSyncValue(SyncValue.of(() -> SyncItem.of(stack.get()), SyncItem.CODEC, SyncItem.of(initial)));
    }

    public static ItemCell of(Supplier<ItemStack> stack) {
        return new ItemCell(stack, ItemStack.EMPTY);
    }

    public static ItemCell constant(ItemStack stack) {
        return new ItemCell(() -> stack, stack);
    }

    public ItemStack getStack() {
        return item.getValue().stack();
    }

    @Override
    public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        if (isMouseOverElement(mouseX, mouseY) && !getStack().isEmpty()) return getStack();
        return super.getXEIIngredientOverMouse(mouseX, mouseY);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = getPositionX(), y = getPositionY();
        UITheme.ITEM_SLOT.draw(graphics, mouseX, mouseY, x, y, SIZE, SIZE);
        var stack = getStack();
        if (!stack.isEmpty()) graphics.renderItem(stack, x + 1, y + 1);
        if (isMouseOverElement(mouseX, mouseY)) {
            RenderSystem.colorMask(true, true, true, false);
            graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, 200, UITheme.SLOT_HOVER_OVERLAY);
            RenderSystem.colorMask(true, true, true, true);
        }
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        var stack = getStack();
        if (stack.isEmpty() || !tooltipTexts.isEmpty() || gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
        gui.getModularUIGui().setHoverTooltip(Screen.getTooltipFromItem(Minecraft.getInstance(), stack), stack, null, null);
    }
}
