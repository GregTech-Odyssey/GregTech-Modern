package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.Level;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 信息图标：与按钮、输入框同高（{@link #SIZE} 见方）的圆形图标，悬停显示说明，放在需要额外解释的控件旁边。
 * 图标随严重度（{@link Level}）：说明（蓝 i，{@link Level#NORMAL}）、成功（绿 ✓）、警告（黄 !）、错误（红 ×）。
 * 说明是标题栏机器说明图标的同款；需要"悬停看说明"的标记一律用本组件，不要用按钮或文字自己拼。
 * 状态会随运行变化时用 {@link Indicator}，本组件的严重度在构建时确定。
 */
public class InfoIcon extends UIElement {

    public static final int SIZE = UISizes.CONTROL_HEIGHT;

    private final Level level;
    private final IGuiTexture icon;

    protected InfoIcon(Level level, Component... tooltips) {
        this.level = level;
        this.icon = UITheme.infoIcon(switch (level) {
            case NORMAL -> 0;
            case WARNING -> 1;
            case ERROR -> 2;
            case GOOD -> 3;
        });
        layout(l -> l.size(SIZE, SIZE));
        setHoverTooltips(tooltips);
    }

    public static InfoIcon of(Level level, Component... tooltips) {
        return new InfoIcon(level, tooltips);
    }

    public static InfoIcon info(String... translationKeys) {
        return (InfoIcon) new InfoIcon(Level.NORMAL).tooltips(translationKeys);
    }

    public static InfoIcon warning(String... translationKeys) {
        return (InfoIcon) new InfoIcon(Level.WARNING).tooltips(translationKeys);
    }

    public static InfoIcon error(String... translationKeys) {
        return (InfoIcon) new InfoIcon(Level.ERROR).tooltips(translationKeys);
    }

    public static InfoIcon success(String... translationKeys) {
        return (InfoIcon) new InfoIcon(Level.GOOD).tooltips(translationKeys);
    }

    public Level getLevel() {
        return level;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        icon.draw(graphics, mouseX, mouseY, getPositionX(), getPositionY(), SIZE, SIZE);
    }
}
