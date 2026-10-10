package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.ElementState;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.Binding;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.render.UIPixels;
import com.gregtechceu.gtceu.uipro.render.UIStates;
import com.gregtechceu.gtceu.uipro.render.UIText;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uipro.styletemplate.UITheme;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.gto.datasynclib.datastream.codec.ByteBufCodecs;
import dev.vfyjxf.taffy.style.FlexWrap;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;

/**
 * 按钮组：一组互相关联的选项，单选（{@link #single}，如机器模式）或多选（{@link #multi}，如一组开关项）。
 * 和普通按钮（触发一次动作）区分开：每个选项有"选中 / 没选中"两种持续状态。
 * <ul>
 * <li>外观：选中的选项整块用确认色（绿，白字），没选中的是普通按钮；左侧有选项标记（{@link UIDraw#optionMark}）——
 * 单选是一盏灯（选中点亮），多选是勾选框（选中打勾），一眼分得出是单选还是多选。</li>
 * <li>排列：默认纵向每个选项一行整宽；{@link #horizontal()} 横排、平分宽度（选项少且名称短时用）；
 * {@link #compact()} 分段选择：一条浅色下凹底上排着各选项，选中项是与底同高、没有底部厚边的绿块，其余只有文字（悬停微暗），
 * 相邻未选中项之间一条细分隔线；每项按文字定宽，适合 dock 这类窄处。</li>
 * <li>{@link #optionTooltips} 给每个选项单独的悬停说明（名称写短、说明写全时用）；不设时悬停显示名称。</li>
 * <li>状态以服务端为准；单选点已选中的项不做任何事，多选点一下取反。</li>
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
    @Nullable
    private final Binding<Integer> current;

    private ButtonGroup(int count, IntFunction<Component> label, IntSupplier current, IntConsumer select) {
        this.multiple = false;
        this.current = addBinding(singleBinding(count, current, select));
        layout(l -> l.column().gapAll(UISizes.GAP));
        for (int i = 0; i < count; i++) {
            addChild(new Option(i, label.apply(i), null));
        }
    }

    private ButtonGroup(int count, IntFunction<Component> label, IntPredicate isOn, Toggle set) {
        this.multiple = true;
        this.current = null;
        layout(l -> l.column().gapAll(UISizes.GAP));
        for (int i = 0; i < count; i++) {
            int index = i;
            addChild(new Option(i, label.apply(i), Binding.bindBool(() -> isOn.test(index), on -> set.set(index, on))));
        }
    }

    private Binding<Integer> singleBinding(int count, IntSupplier current, IntConsumer select) {
        return Binding.bind(current::getAsInt, (Integer index) -> {
            if (current.getAsInt() != index) select.accept(index);
        }, ByteBufCodecs.INT, 0).validate(index -> index >= 0 && index < count && !isOptionDisabled(index));
    }

    private boolean isOptionDisabled(int index) {
        return index < widgets.size() && ElementState.isDisabled(widgets.get(index));
    }

    private boolean isCurrent(int index) {
        return current != null && current.getValue() == index;
    }

    private void select(int index) {
        if (current != null) current.set(index);
    }

    /**
     * 单选：同时只有一个选项选中。
     *
     * @param label   第 i 个选项的名称（两端都会调用，只能用两端都有的数据）
     * @param current 服务端：当前选中的序号
     * @param select  服务端：选中第 i 个
     */
    public static ButtonGroup single(int count, IntFunction<Component> label, IntSupplier current, IntConsumer select) {
        return new ButtonGroup(count, label, current, select);
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
        return new ButtonGroup(count, label, isOn, set);
    }

    /**
     * 图标单选：一排 {@link UISizes#SLOT_SIZE} 见方的图标选项（如切换要看的科技树），选中项整块确认色（与 {@link IconToggle} 开着时一致）。
     * 放不下文字时用它，名称用 {@link #optionTooltips} 给出；有名称的选项用 {@link #single}。
     *
     * @param icon    第 i 个选项的图标（两端都会调用）
     * @param current 服务端：当前选中的序号
     * @param select  服务端：选中第 i 个
     */
    public static ButtonGroup singleIcons(int count, IntFunction<IGuiTexture> icon, IntSupplier current, IntConsumer select) {
        return new ButtonGroup(icon, count, current, select);
    }

    private ButtonGroup(IntFunction<IGuiTexture> icon, int count, IntSupplier current, IntConsumer select) {
        this.multiple = false;
        this.current = addBinding(singleBinding(count, current, select));
        layout(l -> l.row().gapAll(UISizes.GAP).flexWrap(FlexWrap.WRAP));
        for (int i = 0; i < count; i++) {
            addChild(new IconOption(i, icon.apply(i)));
        }
    }

    public ButtonGroup optionDisabled(IntPredicate serverDisabled, @Nullable String reasonKey) {
        int index = 0;
        for (var child : widgets) {
            if (child instanceof Button option) {
                int i = index++;
                option.disabled(() -> serverDisabled.test(i), reasonKey);
            }
        }
        return this;
    }

    /** 横排、平分宽度。 */
    public ButtonGroup horizontal() {
        layout(l -> l.row().height(OPTION_HEIGHT));
        for (var child : widgets) {
            if (child instanceof Option option) option.layout(l -> l.flex(1));
        }
        return this;
    }

    /** 图标选项：方形图标按钮，选中时整块确认色。 */
    private final class IconOption extends Button {

        private IconOption(int index, IGuiTexture icon) {
            super(UISizes.SLOT_SIZE, UISizes.SLOT_SIZE, null, icon);
            bindClientVariant(() -> isCurrent(index) ? UITheme.ButtonVariant.CONFIRM : UITheme.ButtonVariant.DEFAULT);
            setOnClientClick(() -> select(index));
        }
    }

    /** 分段选择（见类注释）：没有选项标记，每项按文字定宽，选项紧挨着排在同一条底上。 */
    public ButtonGroup compact() {
        compact = true;
        layout(l -> l.row().height(OPTION_HEIGHT));
        for (var child : widgets) {
            if (child instanceof Option option) {
                int width = UISizes.widthFor(option.label.getString()) + 2 * UISizes.TEXT_PADDING;
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
    public ButtonGroup optionTooltips(IntFunction<? extends List<? extends Component>> tooltips) {
        int i = 0;
        for (var child : widgets) {
            if (child instanceof Button option) option.tooltips(tooltips.apply(i++).toArray(new Component[0]));
        }
        return this;
    }

    @FunctionalInterface
    private interface Selection {

        boolean getValue();
    }

    /** 一个选项：按钮形状，状态外观见类注释。 */
    private final class Option extends Button {

        private final Component label;
        private final Selection selected;

        private Option(int index, Component label, @Nullable Binding<Boolean> toggle) {
            super(LayoutStyle.AUTO, OPTION_HEIGHT, null, null);
            this.label = label;
            if (toggle != null) {
                var bound = addBinding(toggle);
                this.selected = bound::getValue;
                setOnClientClick(() -> bound.set(!bound.getValue()));
            } else {
                this.selected = () -> isCurrent(index);
                setOnClientClick(() -> select(index));
            }
            setHoverTooltips(label);
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            int x = getPositionX(), y = getPositionY(), w = getSizeWidth(), h = getSizeHeight();
            boolean on = selected.getValue();
            int states = ElementState.resolve(this, mouseX, mouseY);
            boolean hovered = UIStates.has(states, UIStates.HOVERED);
            if (hovered && isClicked) states |= UIStates.PRESSED;
            boolean disabled = UIStates.has(states, UIStates.DISABLED);
            var variant = on ? UITheme.ButtonVariant.CONFIRM : UITheme.ButtonVariant.DEFAULT;
            int faceHeight = h - UISizes.BUTTON_LIP_HEIGHT;
            if (compact) {
                // 分段选择：选中项是四边等宽、没有底部厚边的绿块（与底条同高），其余只画文字，悬停时底微暗
                // （底和分隔线由按钮组画）；文字统一在整个高度里居中，各项对齐
                if (on) {
                    UITheme.SEGMENT_SELECTED.draw(graphics, states, x, y, w, h);
                } else if (hovered) {
                    graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, UITheme.SEGMENT_HOVER);
                }
                UIText.drawCentered(graphics, label.getString(), x + w / 2, UIText.centerY(y, h), w - 2, on ? variant.textColor(true) : UITheme.TEXT);
                if (disabled) UIDraw.disabledHatch(graphics, x, y, w, h);
                return;
            }
            variant.texture().draw(graphics, states, x, y, w, h);
            int markX = x + MARK_PADDING, markY = UIPixels.center(y, faceHeight, UISizes.OPTION_MARK_SIZE);
            UIDraw.optionMark(graphics, markX, markY, multiple, on);
            int textX = markX + UISizes.OPTION_MARK_SIZE + MARK_PADDING;
            UIText.drawLeft(graphics, UIText.fit(label.getString(), x + w - MARK_PADDING - textX), textX, UIText.centerY(y, faceHeight), variant.textColor(true));
            if (disabled) UIDraw.disabledHatch(graphics, x, y, w, faceHeight);
        }
    }
}
