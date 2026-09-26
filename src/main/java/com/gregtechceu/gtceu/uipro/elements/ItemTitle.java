package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncItem;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public class ItemTitle extends UIElement {

    public static final int HEIGHT = UISizes.CONTROL_HEIGHT;

    public ItemTitle(int width, Supplier<ItemStack> stack, Supplier<Component> name) {
        layout(l -> l.row().width(width).height(HEIGHT).gapAll(UISizes.GAP).alignCenter());
        var text = TextLine.of(0, () -> Component.literal(ChatFormatting.stripFormatting(name.get().getString()))).setColor(UITheme.TEXT);
        text.layout(l -> l.flex(1));
        addChildren(new Icon(stack), text);
    }

    public static ItemTitle of(Supplier<ItemStack> stack, Supplier<Component> name) {
        return new ItemTitle(LayoutStyle.AUTO, stack, name);
    }

    private static final class Icon extends UIElement {

        private final SyncValue<SyncItem> item;
        private ItemStack drawn = ItemStack.EMPTY;
        @Nullable
        private ItemStackTexture texture;

        private Icon(Supplier<ItemStack> stack) {
            layout(l -> l.size(ItemView.SIZE, ItemView.SIZE));
            item = addSyncValue(SyncValue.of(() -> SyncItem.of(stack.get()), SyncItem.CODEC, SyncItem.EMPTY));
        }

        @Override
        public @Nullable Object getXEIIngredientOverMouse(double mouseX, double mouseY) {
            var stack = item.getValue().stack();
            if (isMouseOverElement(mouseX, mouseY) && !stack.isEmpty()) return stack;
            return super.getXEIIngredientOverMouse(mouseX, mouseY);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            var stack = item.getValue().stack();
            if (stack != drawn || texture == null) {
                drawn = stack;
                texture = new ItemStackTexture(stack);
            }
            if (!stack.isEmpty()) texture.draw(graphics, mouseX, mouseY, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
        }
    }
}
