package com.gregtechceu.gtceu.client.tooltip;

import com.gregtechceu.gtceu.api.tooltip.TipContents;
import com.gregtechceu.gtceu.api.tooltip.TipLineComponent;
import com.gregtechceu.gtceu.api.tooltip.TipRole;
import com.gregtechceu.gtceu.api.tooltip.Tips;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderTooltipEvent;

import com.mojang.datafixers.util.Either;

import java.util.List;

/**
 * 把物品提示里的角色行换成像素对齐的提示框组件，并在分组之间留白。
 * <b>仅用于物品/方块 tooltip（悬停提示框），不是通用的文字渲染设施</b>：机器界面、说明页、显示行、聊天一律用普通
 * {@code Component} 与 uipro 文本组件，不要拿这里的行或规格去拼。
 */
@OnlyIn(Dist.CLIENT)
public final class TipTooltipLayout {

    private static final int MAX_WIDTH = 260;
    private static final int GROUP_GAP = 5;

    private TipTooltipLayout() {}

    private static TipContents merged(Component line, TipContents tip) {
        if (line.getSiblings().isEmpty() && line.getStyle().isEmpty()) return tip;
        MutableComponent body = Component.empty().withStyle(line.getStyle()).append(tip.body());
        for (Component sibling : line.getSiblings()) body.append(sibling);
        return (TipContents) TipContents.line(tip.role(), body).getContents();
    }

    private static int firstHeading(List<Either<FormattedText, TooltipComponent>> elements) {
        for (int i = 1; i < elements.size(); i++) {
            var left = elements.get(i).left();
            if (left.isPresent() && left.get() instanceof Component c && c.getContents() instanceof TipContents tip &&
                    tip.role() == TipRole.HEADING)
                return i;
        }
        return elements.size();
    }

    public static void gather(RenderTooltipEvent.GatherComponents event) {
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        int maxWidth = Math.max(120, Math.min(MAX_WIDTH, event.getScreenWidth() / 2));
        int firstHeading = firstHeading(elements);
        boolean previousIndented = false;
        TipRole previousRole = null;
        for (int i = 0; i < elements.size(); i++) {
            var left = elements.get(i).left();
            if (left.isPresent() && left.get() instanceof Component c && c.getContents() instanceof TipContents tip) {
                if (i < firstHeading && tip.role() == TipRole.FEATURE) tip = (TipContents) TipContents.line(TipRole.SUMMARY, tip.body()).getContents();
                TipRole role = tip.role();
                int pad = 0;
                if (i > 1 && (role == TipRole.HEADING || role == TipRole.FOOTER ||
                        (role.depth() == 0 && previousIndented) || (role == TipRole.LORE && previousRole != TipRole.LORE))) {
                    pad = GROUP_GAP;
                }
                elements.set(i, Either.right(new TipLineComponent(merged(c, tip), pad, maxWidth)));
                previousIndented = role.depth() > 0;
                previousRole = role;
            } else {
                if (left.isPresent() && left.get() instanceof Component c) {
                    Component resolved = Tips.resolveDynamic(c);
                    if (resolved != c) elements.set(i, Either.left(resolved));
                }
                previousIndented = false;
                previousRole = null;
            }
        }
    }
}
