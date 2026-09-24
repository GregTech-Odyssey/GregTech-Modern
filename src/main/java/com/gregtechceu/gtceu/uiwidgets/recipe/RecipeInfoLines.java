package com.gregtechceu.gtceu.uiwidgets.recipe;

import com.gregtechceu.gtceu.api.recipe.ui.RecipeInfoBuilder;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;

import com.google.common.base.Suppliers;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 配方页信息区的内容：状态面板的行和面板下方的额外展示槽。记下各方追加的内容，由配方页据此建控件。
 * 只需要尺寸时用 {@link Counter}（只数数，不调用任何 Supplier）。
 * <p>
 * 经 {@link RecipeInfoBuilder} 追加的数值只取一次（第一次显示时），之后复用；随电压档变化的核心参数行用 {@link #dynamicLine}。
 */
public final class RecipeInfoLines implements RecipeInfoBuilder {

    /** 一行：{@code labelKey} 为 null 时是整句。 */
    public record Line(@Nullable String labelKey, Supplier<Component> value) {}

    private final List<Line> lines = new ArrayList<>();
    private final List<Supplier<Widget>> slots = new ArrayList<>(2);

    @Override
    public RecipeInfoLines line(String labelKey, Supplier<Component> value) {
        lines.add(new Line(labelKey, Suppliers.memoize(() -> nonNull(value.get()))));
        return this;
    }

    @Override
    public RecipeInfoLines sentence(Supplier<Component> text) {
        lines.add(new Line(null, Suppliers.memoize(() -> nonNull(text.get()))));
        return this;
    }

    /** 数值随页面状态变化（如超频预览的电压档）的一行，每次取值都调用 {@code value}，调用方负责让它廉价。 */
    public RecipeInfoLines dynamicLine(String labelKey, Supplier<Component> value) {
        lines.add(new Line(labelKey, value));
        return this;
    }

    @Override
    public RecipeInfoLines slot(Supplier<Widget> slot) {
        slots.add(slot);
        return this;
    }

    private static Component nonNull(@Nullable Component value) {
        return value == null ? Component.empty() : value;
    }

    public List<Line> lines() {
        return lines;
    }

    public List<Supplier<Widget>> slots() {
        return slots;
    }

    /** 只数行数和槽数，算配方页尺寸用。 */
    public static final class Counter implements RecipeInfoBuilder {

        private int labeled;
        private int sentences;
        private int slots;

        @Override
        public Counter line(String labelKey, Supplier<Component> value) {
            labeled++;
            return this;
        }

        @Override
        public Counter sentence(Supplier<Component> text) {
            sentences++;
            return this;
        }

        @Override
        public Counter slot(Supplier<Widget> slot) {
            slots++;
            return this;
        }

        /** "名称 …… 数值"行数。 */
        public int labeled() {
            return labeled;
        }

        /** 整句行数。 */
        public int sentences() {
            return sentences;
        }

        public int slots() {
            return slots;
        }
    }
}
