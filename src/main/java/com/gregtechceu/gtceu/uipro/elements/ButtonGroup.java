package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.styletemplate.OreSprites;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;

/**
 * 按钮组：一组互相关联的选项，单选（{@link #single}，如机器模式）或多选（{@link #multi}，如一组开关项）。
 * 和普通按钮（触发一次动作）区分开：每个选项有"选中 / 没选中"两种持续状态。
 * <ul>
 * <li>外观：选中的选项整块用确认色（绿，白字），没选中的是普通按钮；左侧有选项标记（{@link UITheme#drawOptionMark}）——
 * 单选是一盏灯（选中点亮），多选是勾选框（选中打勾），一眼分得出是单选还是多选。</li>
 * <li>排列：默认纵向每个选项一行整宽；{@link #horizontal()} 横排、平分宽度（选项少且名称短时用）；
 * {@link #compact()} 分段选择：一条浅色下凹底上排着各选项，选中项是与底同高、没有底部厚边的绿块，其余只有文字（悬停微暗），
 * 相邻未选中项之间一条细分隔线；每项按文字定宽，适合 dock 这类窄处。</li>
 * <li>{@link #tooltips} 给每个选项单独的悬停说明（名称写短、说明写全时用）；不设时悬停显示名称。</li>
 * <li>状态以服务端为准：每个选项是否选中由服务端判定、经选项自己的同步值下发；点击只在服务端执行，
 * 单选点已选中的项不做任何事，多选点一下取反。</li>
 * <li>禁用（{@link #disabled}）与其他元素一样继承：整组禁用时所有选项叠斜纹、不能点。</li>
 * </ul>
 * 选项个数与名称两端建界面时各取一次，必须两端一致（控件树一致）。
 */
public class ButtonGroup extends UIElement {

    public static final int OPTION_HEIGHT = UISizes.CONTROL_HEIGHT;
    /// 标记左侧、标记与文字之间的留白
    private static final int MARK_PADDING = 4;

    private final boolean multiple;
    private boolean compact;

    private ButtonGroup(boolean multiple, int count, IntFunction<Component> label, IntPredicate serverSelected, IntConsumer serverClick) {
        this.multiple = multiple;
        layout(l -> l.column().gapAll(UISizes.GAP));
        for (int i = 0; i < count; i++) {
            addChild(new Option(i, label.apply(i), serverSelected, serverClick));
        }
    }

    /**
     * 单选：同时只有一个选项选中。
     *
     * @param label   第 i 个选项的名称（两端都会调用，只能用两端都有的数据）
     * @param current 服务端：当前选中的序号
     * @param select  服务端：选中第 i 个
     */
    public static ButtonGroup single(int count, IntFunction<Component> label, IntSupplier current, IntConsumer select) {
        return new ButtonGroup(false, count, label, i -> current.getAsInt() == i, i -> {
            if (current.getAsInt() != i) select.accept(i);
        });
    }

    /** 多选的写回：第 {@code index} 项改为 {@code on}。 */
    @FunctionalInterface
    public interface Toggle {

        void set(int index, boolean on);
    }

    /**
     * 多选：每个选项各自开关。
     *
     * @param isOn 服务端：第 i 项是否选中
     * @param set  服务端：把第 i 项改为选中 / 不选中
     */
    public static ButtonGroup multi(int count, IntFunction<Component> label, IntPredicate isOn, Toggle set) {
        return new ButtonGroup(true, count, label, isOn, i -> set.set(i, !isOn.test(i)));
    }

    /** 横排、平分宽度。 */
    public ButtonGroup horizontal() {
        layout(l -> l.row().height(OPTION_HEIGHT));
        for (var child : widgets) {
            if (child instanceof Option option) option.layout(l -> l.flex(1));
        }
        return this;
    }

    /** 分段选择（见类注释）：没有选项标记，每项按文字定宽，选项紧挨着排在同一条底上。 */
    public ButtonGroup compact() {
        compact = true;
        layout(l -> l.row().height(OPTION_HEIGHT));
        for (var child : widgets) {
            if (child instanceof Option option) {
                int width = UISizes.textWidth(option.label.getString()) + 2 * UISizes.TEXT_PADDING;
                option.layout(l -> l.width(width));
            }
        }
        return this;
    }

    /** 分段选择的底与分隔线（选项自己画在上面）。 */
    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (compact) {
            int x = getPositionX(), y = getPositionY();
            UITheme.PANEL.draw(graphics, mouseX, mouseY, x, y, getSizeWidth(), getSizeHeight());
            Option previous = null;
            for (var child : widgets) {
                if (!(child instanceof Option option)) continue;
                if (previous != null && !previous.selected.getValue() && !option.selected.getValue()) {
                    int lineX = option.getPositionX();
                    graphics.fill(lineX, y + 3, lineX + 1, y + getSizeHeight() - 3, UITheme.DOCK_SEPARATOR);
                }
                previous = option;
            }
        }
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    /** 每个选项单独的悬停说明（两端都会调用）。 */
    public ButtonGroup tooltips(IntFunction<Component> tooltip) {
        int i = 0;
        for (var child : widgets) {
            if (child instanceof Option option) option.setHoverTooltips(tooltip.apply(i++));
        }
        return this;
    }

    /** 一个选项：按钮形状，状态外观见类注释。 */
    private final class Option extends Button {

        private final Component label;
        private final SyncValue<Boolean> selected;

        private Option(int index, Component label, IntPredicate serverSelected, IntConsumer serverClick) {
            super(LayoutStyle.AUTO, OPTION_HEIGHT, null, null);
            this.label = label;
            this.selected = addSyncValue(SyncValue.of(() -> serverSelected.test(index), SyncValue.BOOLEAN, false));
            setOnServerClick(() -> serverClick.accept(index));
            setHoverTooltips(label);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
            boolean on = selected.getValue();
            boolean disabled = isDisabled();
            boolean hovered = !disabled && isMouseOverElement(mouseX, mouseY);
            var variant = on ? UITheme.ButtonVariant.CONFIRM : UITheme.ButtonVariant.DEFAULT;
            int faceHeight = h - UITheme.BUTTON_LIP;
            if (compact) {
                // 分段选择：选中项是四边等宽、没有底部厚边的绿块（与底条同高），其余只画文字，悬停时底微暗
                // （底和分隔线由按钮组画）；文字统一在整个高度里居中，各项对齐
                if (on) {
                    var sprite = hovered ? (isClicked ? OreSprites.BTN_PRESSED_SMALL_GREEN : OreSprites.BTN_HOVER_SMALL_GREEN) : OreSprites.BTN_DEFAULT_SMALL_GREEN;
                    sprite.draw(graphics, x, y, w, h);
                } else if (hovered) {
                    graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, UITheme.SEGMENT_HOVER);
                }
                UITheme.drawCenteredText(graphics, label.getString(), x + w / 2, y + (h - 8) / 2, w - 2,
                        on ? variant.textColor(true) : UITheme.TEXT, false);
                if (disabled) UITheme.drawDisabled(graphics, x, y, w, h);
                return;
            } else {
                UITheme.drawButton(graphics, x, y, w, h, variant, hovered, hovered && isClicked, true);
                int markX = x + MARK_PADDING, markY = y + (faceHeight - UITheme.OPTION_MARK_SIZE) / 2;
                UITheme.drawOptionMark(graphics, markX, markY, multiple, on);
                int textX = markX + UITheme.OPTION_MARK_SIZE + MARK_PADDING;
                var font = Minecraft.getInstance().font;
                var text = UITheme.clip(font, label.getString(), x + w - MARK_PADDING - textX);
                graphics.drawString(font, text, textX, y + (faceHeight - 8) / 2, variant.textColor(true), false);
            }
            if (disabled) UITheme.drawDisabled(graphics, x, y, w, faceHeight);
        }
    }
}
