package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * 槽位外观的点选格：物品槽底图（{@link UITheme#ITEM_SLOT}，18 见方）里画一个图标，悬停时与物品槽一样蒙白，点击执行动作。
 * 用于"从一组物品里选一个"（编程电路编号等）：与物品槽排在同一网格里对得齐，一行 {@link UISizes#SLOTS_PER_ROW} 个；
 * 连成一片也不会像按钮那样每行底下多出一道台阶。
 * <p>
 * 点击、选中（{@link #setSelected}，统一选中框）、禁用（{@link #disabled}，统一斜纹 + 悬停先"禁止操作"再原因）都与 {@link Button} 相同。
 */
public class SlotButton extends Button {

    public static final int SIZE = UISizes.SLOT;

    private final IGuiTexture slotIcon;

    protected SlotButton(IGuiTexture icon) {
        super(SIZE, SIZE, null, null);
        this.slotIcon = icon;
    }

    public static SlotButton of(IGuiTexture icon) {
        return new SlotButton(icon);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int x = getPositionX(), y = getPositionY();
        boolean disabled = isDisabled();
        UITheme.ITEM_SLOT.draw(graphics, mouseX, mouseY, x, y, SIZE, SIZE);
        slotIcon.draw(graphics, mouseX, mouseY, x + 1, y + 1, SIZE - 2, SIZE - 2);
        if (!disabled && isMouseOverElement(mouseX, mouseY)) {
            RenderSystem.colorMask(true, true, true, false);
            graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, 200, UITheme.SLOT_HOVER_OVERLAY);
            RenderSystem.colorMask(true, true, true, true);
        }
        if (disabled) UITheme.drawDisabled(graphics, x, y, SIZE, SIZE);
    }
}
