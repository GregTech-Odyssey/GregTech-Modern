package com.gregtechceu.gtceu.api.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * 提示框里一行带角色的提示（{@code ClientTipLine} 的数据）。
 * <b>仅用于物品/方块 tooltip（悬停提示框），不是通用的文字渲染设施</b>：机器界面、说明页、显示行、聊天一律用普通
 * {@code Component} 与 uipro 文本组件，不要拿这里的行或规格去拼。
 */
public record TipLineComponent(TipContents contents, int padTop, int maxWidth) implements TooltipComponent {}
