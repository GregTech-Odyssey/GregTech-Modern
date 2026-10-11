package com.gregtechceu.gtceu.client.renderer.item;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;
import com.gregtechceu.gtceu.api.item.IGTTool;
import com.gregtechceu.gtceu.api.item.component.IDurabilityBar;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.api.tooltip.TipTheme;
import com.gregtechceu.gtceu.client.util.DrawUtil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.ints.IntIntPair;

public final class ToolChargeBarRenderer {

    private static final int BAR_W = 12;

    private static final int colorShadow = TipTheme.ENERGY_BAR_SHADOW;

    private static final int colorBarLeftDurability = TipTheme.DURABILITY_BAR_LEFT;
    private static final int colorBarRightDurability = TipTheme.DURABILITY_BAR_RIGHT;

    private static final int colorBarLeftDepleted = TipTheme.ENERGY_BAR_DEPLETED_LEFT;
    private static final int colorBarRightDepleted = TipTheme.ENERGY_BAR_DEPLETED_RIGHT;

    public static void render(GuiGraphics graphics, int level, int xPosition, int yPosition, int offset, boolean shadow,
                              int left, int right, boolean doDepletedColor) {
        if (doDepletedColor && level <= BAR_W / 4) {
            left = colorBarLeftDepleted;
            right = colorBarRightDepleted;
        }

        int x = xPosition + 2;
        int y = yPosition + 13 - offset;
        graphics.fill(RenderType.guiOverlay(), x, y, x + 13, y + (shadow ? 2 : 1), 400, colorShadow);
        DrawUtil.fillHorizontalGradient(graphics, RenderType.guiOverlay(), x, y, x + level, y + 1, left, right, 400);
    }

    public static void renderBarsTool(GuiGraphics graphics, IGTTool tool, ItemStack stack, int xPosition,
                                      int yPosition) {
        boolean renderedDurability = false;
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.getBoolean(ToolHelper.UNBREAKABLE_KEY)) {
            renderedDurability = renderDurabilityBar(graphics, stack.getBarWidth(), xPosition, yPosition);
        }
        if (tool.isElectric()) {
            IElectricItem electricItem = GTCapabilityHelper.getElectricItem(stack);
            if (electricItem != null) {
                renderElectricBar(graphics, electricItem.getCharge(), electricItem.getMaxCharge(), xPosition, yPosition,
                        renderedDurability);
            }
        }
    }

    public static boolean renderElectricBar(GuiGraphics graphics, long charge, long maxCharge, int xPosition,
                                            int yPosition, boolean renderedDurability) {
        int level = maxCharge > 0 ? Math.round(Math.max(0, charge) * 13.0F / maxCharge) : 0;
        boolean depleted = TipTheme.energyDepleted(charge, maxCharge);
        render(graphics, level, xPosition, yPosition, renderedDurability ? 2 : 0, true,
                depleted ? TipTheme.ENERGY_BAR_DEPLETED_LEFT : TipTheme.ENERGY_BAR_LEFT,
                depleted ? TipTheme.ENERGY_BAR_DEPLETED_RIGHT : TipTheme.ENERGY_BAR_RIGHT, false);
        return true;
    }

    public static boolean renderDurabilityBar(GuiGraphics graphics, ItemStack stack, IDurabilityBar manager,
                                              int xPosition, int yPosition) {
        float level = manager.getDurabilityForDisplay(stack);
        if (level == 0.0 && !manager.showEmptyBar(stack)) return false;
        if (level == 1.0 && !manager.showFullBar(stack)) return false;
        IntIntPair colors = manager.getDurabilityColorsForDisplay(stack);
        boolean doDepletedColor = manager.doDamagedStateColors(stack);
        int left = colors != null ? colors.leftInt() : colorBarLeftDurability;
        int right = colors != null ? colors.rightInt() : colorBarRightDurability;
        render(graphics, manager.getBarWidth(stack), xPosition, yPosition, 0, true, left, right, doDepletedColor);
        return true;
    }

    private static boolean renderDurabilityBar(GuiGraphics graphics, int level, int xPosition, int yPosition) {
        render(graphics, level, xPosition, yPosition, 0, true, colorBarLeftDurability, colorBarRightDurability, true);
        return true;
    }

    private ToolChargeBarRenderer() {}
}
