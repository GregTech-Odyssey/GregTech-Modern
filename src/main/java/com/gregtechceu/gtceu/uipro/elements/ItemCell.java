package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.UIIngredient;
import com.gregtechceu.gtceu.uipro.data.RPC;
import com.gregtechceu.gtceu.uipro.data.SyncItem;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.UICodecs;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.util.ClickData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class ItemCell extends UIElement {

    public static final int SIZE = UISizes.SLOT_SIZE;

    private final SyncValue<SyncItem> item;
    private boolean plain;
    @Nullable
    private RPC<ClickData> click;

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

    public ItemCell setOnServerClick(BiConsumer<Player, ClickData> onServerClick) {
        this.click = getChannel().addRPC(UICodecs.CLICK, onServerClick).limit(2);
        return this;
    }

    public ItemStack getStack() {
        return item.getValue().stack();
    }

    @Override
    public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
        if (isMouseOverElement(mouseX, mouseY) && !getStack().isEmpty()) return UIIngredient.of(getStack());
        return super.getXEIIngredientOverMouse(mouseX, mouseY);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = getPositionX(), y = getPositionY();
        if (!plain) UITheme.ITEM_SLOT.draw(graphics, mouseX, mouseY, x, y, SIZE, SIZE);
        var stack = getStack();
        if (!stack.isEmpty()) graphics.renderItem(stack, x + 1, y + 1);
        boolean disabled = click != null && isDisabled();
        if (!plain && !disabled && isMouseOverElement(mouseX, mouseY)) UIDraw.hoverOverlay(graphics, x, y, SIZE, SIZE);
        if (disabled) UIDraw.disabledHatch(graphics, x, y, SIZE, SIZE);
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (click == null || (button != 0 && button != 1) || !isMouseOverElement(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
        if (isDisabled()) return true;
        click.send(new ClickData());
        playButtonClickSound();
        return true;
    }

    @Override
    public boolean hasOwnTooltip(int mouseX, int mouseY) {
        return super.hasOwnTooltip(mouseX, mouseY) || !getStack().isEmpty();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
        if (click != null) {
            drawInteractiveTooltip(mouseX, mouseY);
            return;
        }
        var stack = getStack();
        if (stack.isEmpty() || !tooltipTexts.isEmpty() || gui == null || gui.getModularUIGui() == null || !isMouseOverElement(mouseX, mouseY)) return;
        gui.getModularUIGui().setHoverTooltip(Screen.getTooltipFromItem(Minecraft.getInstance(), stack), stack, null, null);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawTooltipTexts(int mouseX, int mouseY) {
        if (click == null) super.drawTooltipTexts(mouseX, mouseY);
    }

    @OnlyIn(Dist.CLIENT)
    private void drawInteractiveTooltip(int mouseX, int mouseY) {
        if (gui == null || gui.getModularUIGui() == null || !ElementState.isHovered(this, mouseX, mouseY)) return;
        if (!gui.getModularUIContainer().getCarried().isEmpty()) return;
        var stack = getStack();
        var lines = new ArrayList<Component>();
        if (!stack.isEmpty()) lines.addAll(Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
        lines.addAll(tooltipTexts);
        if (ElementState.showDisabledTooltip(this, mouseX, mouseY, lines)) return;
        if (!lines.isEmpty()) gui.getModularUIGui().setHoverTooltip(lines, stack, null, null);
    }
}
