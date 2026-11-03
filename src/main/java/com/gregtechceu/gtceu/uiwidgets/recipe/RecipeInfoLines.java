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
 * 配方页信息区的内容：面板内的展示槽和面板下方的额外展示槽。记下各方追加的内容，由配方页据此建控件。
 * 只需要尺寸时用 {@link Counter}。
 */
public final class RecipeInfoLines implements RecipeInfoBuilder {

    public enum Kind {
        PROPERTY,
        REQUIREMENT,
        SENTENCE,
        LINK
    }

    /** 一行：{@code label} 为 null 时是整句。 */
    public record Line(Kind kind, @Nullable Component label, Supplier<Component> value, @Nullable Runnable onClick) {}

    private final List<Line> lines = new ArrayList<>();
    private final List<Supplier<Widget>> slots = new ArrayList<>(2);
    private final ArrayList<Supplier<Widget>> panelSlots = new ArrayList<>(2);

    @Override
    public RecipeInfoLines line(Component label, Supplier<Component> value) {
        lines.add(new Line(Kind.PROPERTY, label, memoize(value), null));
        return this;
    }

    @Override
    public RecipeInfoLines requirement(Component label, Supplier<Component> value) {
        lines.add(new Line(Kind.REQUIREMENT, label, memoize(value), null));
        return this;
    }

    @Override
    public RecipeInfoLines sentence(Supplier<Component> text) {
        lines.add(new Line(Kind.SENTENCE, null, memoize(text), null));
        return this;
    }

    @Override
    public RecipeInfoLines link(Supplier<Component> text, Runnable onClick) {
        lines.add(new Line(Kind.LINK, null, memoize(text), onClick));
        return this;
    }

    @Override
    public RecipeInfoLines slot(Supplier<Widget> slot) {
        slots.add(slot);
        return this;
    }

    @Override
    public RecipeInfoLines panelSlot(Supplier<Widget> slot) {
        panelSlots.add(slot);
        return this;
    }

    private static Supplier<Component> memoize(Supplier<Component> value) {
        return Suppliers.memoize(() -> nonNull(value.get()));
    }

    static Component nonNull(@Nullable Component value) {
        return value == null ? Component.empty() : value;
    }

    public List<Line> lines() {
        return lines;
    }

    public List<Supplier<Widget>> slots() {
        return slots;
    }

    public ArrayList<Supplier<Widget>> panelSlots() {
        return panelSlots;
    }

    /** 只数行数和槽数，算配方页尺寸用。 */
    public static final class Counter implements RecipeInfoBuilder {

        private final List<Line> lines = new ArrayList<>();
        private int slots;
        private int panelSlots;

        @Override
        public Counter line(Component label, Supplier<Component> value) {
            lines.add(new Line(Kind.PROPERTY, label, value, null));
            return this;
        }

        @Override
        public Counter requirement(Component label, Supplier<Component> value) {
            lines.add(new Line(Kind.REQUIREMENT, label, value, null));
            return this;
        }

        @Override
        public Counter sentence(Supplier<Component> text) {
            lines.add(new Line(Kind.SENTENCE, null, text, null));
            return this;
        }

        @Override
        public Counter link(Supplier<Component> text, Runnable onClick) {
            lines.add(new Line(Kind.LINK, null, text, onClick));
            return this;
        }

        @Override
        public Counter slot(Supplier<Widget> slot) {
            slots++;
            return this;
        }

        @Override
        public Counter panelSlot(Supplier<Widget> slot) {
            panelSlots++;
            return this;
        }

        public List<Line> lines() {
            return lines;
        }

        public int slots() {
            return slots;
        }

        public int panelSlots() {
            return panelSlots;
        }
    }
}
