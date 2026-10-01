package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 物品展示：只显示、不可交互的物品图标（或任意图标贴图），默认与按钮、输入框同高（{@link #SIZE} 见方），
 * 图标按尺寸缩放铺满。需要悬浮说明时用 {@code setHoverTooltips}。
 */
public class ItemView extends UIElement {

    public static final int SIZE = UISizes.CONTROL_HEIGHT;

    private final IGuiTexture icon;

    protected ItemView(int size, IGuiTexture icon) {
        this.icon = icon;
        layout(l -> l.size(size, size));
    }

    public static ItemView of(IGuiTexture icon) {
        return new ItemView(SIZE, icon);
    }

    public static ItemView of(int size, IGuiTexture icon) {
        return new ItemView(size, icon);
    }

    public static ItemView of(ItemStack stack) {
        return new ItemView(SIZE, new ItemStackTexture(stack));
    }

    public static ItemView of(int size, ItemStack stack) {
        return new ItemView(size, new ItemStackTexture(stack));
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        icon.draw(graphics, mouseX, mouseY, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
    }
}
