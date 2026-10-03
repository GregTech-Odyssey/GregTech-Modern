package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncItem;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class ItemCell extends UIElement {

    public static final int SIZE = UISizes.SLOT_SIZE;

    private final SyncValue<SyncItem> item;
    private boolean plain;

    protected ItemCell(Supplier<ItemStack> stack, ItemStack initial) {
        layout(l -> l.size(SIZE, SIZE));
        this.item = addSyncValue(SyncValue.ofItem(stack, initial));
    }

    public static ItemCell of(Supplier<ItemStack> stack) {
        return new ItemCell(stack, ItemStack.EMPTY);
    }

    public static ItemCell constant(ItemStack stack) {
        return new ItemCell(() -> stack, stack);
    }

    public ItemCell plain() {
        this.plain = true;
        return this;
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
        if (!plain) UITheme.ITEM_SLOT.draw(graphics, mouseX, mouseY, x, y, SIZE, SIZE);
        var stack = getStack();
        if (!stack.isEmpty()) graphics.renderItem(stack, x + 1, y + 1);
        if (!plain && isMouseOverElement(mouseX, mouseY)) UIDraw.hoverOverlay(graphics, x, y, SIZE, SIZE);
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean hasOwnTooltip(int mouseX, int mouseY) {
        return super.hasOwnTooltip(mouseX, mouseY) || !getStack().isEmpty();
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
