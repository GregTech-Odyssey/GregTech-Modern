package com.gregtechceu.gtceu.api.tooltip;

import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 一件物品或机器的完整提示规格：按槽位排序的分组，静态部分构建一次，按住 Shift 展开详情。
 */
public final class TipSpec implements Supplier<List<Component>> {

    private final List<Group> groups;
    private final boolean dynamic;
    private final boolean hasDetails;
    private final List<Component> collapsed;
    private final List<Component> expanded;

    private TipSpec(List<Group> groups) {
        this.groups = groups;
        boolean dyn = false, details = false;
        for (Group g : groups) {
            dyn |= g.dynamic();
            details |= g.shiftOnly;
        }
        this.dynamic = dyn;
        this.hasDetails = details;
        this.collapsed = dyn ? List.of() : Collections.unmodifiableList(collect(false));
        this.expanded = dyn || !details ? collapsed : Collections.unmodifiableList(collect(true));
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<Component> get() {
        boolean shift = hasDetails && GTUtil.isShiftDown();
        if (dynamic) return collect(shift);
        return shift ? expanded : collapsed;
    }

    public Component[] toArray() {
        return get().toArray(Component[]::new);
    }

    @SuppressWarnings("unchecked")
    public Supplier<Component>[] lineSuppliers() {
        List<Component> lines = get();
        Supplier<Component>[] result = new Supplier[lines.size()];
        for (int i = 0; i < result.length; i++) {
            Component line = lines.get(i);
            result[i] = () -> line;
        }
        return result;
    }

    private List<Component> collect(boolean shift) {
        List<Component> out = new ObjectArrayList<>();
        for (Group g : groups) {
            if (g.shiftOnly && !shift) continue;
            if (g.heading != null) out.add(TipContents.line(TipRole.HEADING, g.heading));
            g.lines.appendTo(out);
        }
        if (hasDetails && !shift) {
            out.add(TipContents.line(TipRole.FOOTER, Component.translatable("gtceu.tooltip.hold_shift")));
        }
        return out;
    }

    private record Group(TipSlot slot, int order, @Nullable Component heading, boolean shiftOnly, Lines lines) {

        boolean dynamic() {
            return lines.dynamic;
        }
    }

    public static final class Builder {

        private final List<Group> groups = new ArrayList<>();

        private Builder() {}

        public Builder summary(Component text) {
            return group(TipSlot.SUMMARY, null, l -> l.line(TipRole.SUMMARY, text));
        }

        public Builder stats(Consumer<Lines> body) {
            return group(TipSlot.STATS, null, body);
        }

        public Builder stat(StatKind kind, Object... args) {
            return stats(l -> l.stat(kind, args));
        }

        public Builder lines(Consumer<Lines> body) {
            return group(TipSlot.FEATURES, null, body);
        }

        public Builder group(@Nullable Component heading, Consumer<Lines> body) {
            return group(TipSlot.FEATURES, heading, body);
        }

        public Builder group(TipSlot slot, @Nullable Component heading, Consumer<Lines> body) {
            return add(slot, heading, false, body);
        }

        public Builder details(@Nullable Component heading, Consumer<Lines> body) {
            return add(TipSlot.DETAILS, heading, true, body);
        }

        public Builder lore(Component text) {
            return group(TipSlot.LORE, null, l -> l.line(TipRole.LORE, text));
        }

        public Builder include(TipSpec other) {
            groups.addAll(other.groups);
            return this;
        }

        public Lines open(TipSlot slot, @Nullable Component heading, boolean shiftOnly) {
            return open(slot, 0, heading, shiftOnly);
        }

        public Lines open(TipSlot slot, int order, @Nullable Component heading, boolean shiftOnly) {
            Lines lines = new Lines();
            groups.add(new Group(slot, order, heading, shiftOnly, lines));
            return lines;
        }

        public boolean isEmpty() {
            for (Group g : groups) if (!g.lines.entries.isEmpty() || g.heading != null) return false;
            return true;
        }

        private Builder add(TipSlot slot, @Nullable Component heading, boolean shiftOnly, Consumer<Lines> body) {
            body.accept(open(slot, heading, shiftOnly));
            return this;
        }

        public TipSpec build() {
            List<Group> sorted = new ArrayList<>(groups);
            sorted.removeIf(g -> g.lines.entries.isEmpty() && g.heading == null);
            sorted.sort(Comparator.<Group>comparingInt(g -> g.slot.ordinal()).thenComparingInt(g -> g.order));
            return new TipSpec(List.copyOf(sorted));
        }
    }

    public static final class Lines {

        private final List<Entry> entries = new ArrayList<>();
        private boolean dynamic;
        private boolean keepOrder;

        private Lines() {}

        public Lines keepOrder() {
            keepOrder = true;
            return this;
        }

        public Lines line(TipRole role, Component body) {
            Component line = TipContents.line(role, body);
            entries.add(new Entry(role.isControl(), () -> line, null));
            return this;
        }

        public Lines lines(Supplier<? extends List<? extends Component>> source) {
            dynamic = true;
            entries.add(new Entry(false, null, source));
            return this;
        }

        public Lines lines(List<? extends Component> source) {
            for (Component c : source) {
                Component line = c.getContents() instanceof TipContents ? c : TipContents.line(TipRole.PLAIN, c);
                boolean control = c.getContents() instanceof TipContents tip && tip.role().isControl();
                entries.add(new Entry(control, () -> line, null));
            }
            return this;
        }

        public Lines dynamic(TipRole role, Supplier<Component> body) {
            dynamic = true;
            entries.add(new Entry(role.isControl(), () -> TipContents.line(role, body.get()), null));
            return this;
        }

        private void appendTo(List<Component> out) {
            if (keepOrder) {
                for (Entry e : entries) e.appendTo(out);
                return;
            }
            for (Entry e : entries) if (!e.control) e.appendTo(out);
            for (Entry e : entries) if (e.control) e.appendTo(out);
        }

        private record Entry(boolean control, @Nullable Supplier<Component> line,
                             @Nullable Supplier<? extends List<? extends Component>> many) {

            void appendTo(List<Component> out) {
                if (line != null) {
                    out.add(line.get());
                    return;
                }
                List<? extends Component> list = many.get();
                if (list == null) return;
                for (Component c : list) {
                    out.add(c.getContents() instanceof TipContents ? c : TipContents.line(TipRole.PLAIN, c));
                }
            }
        }

        public Lines stat(StatKind kind, Object... args) {
            return line(TipRole.STAT, kind.line(args));
        }

        public Lines stat(StatKind kind, Component value) {
            return line(TipRole.STAT, kind.line(value));
        }

        public Lines energy(StatKind kind, long eu, int tier) {
            return line(TipRole.STAT, Tips.energyBody(kind, eu, tier));
        }

        public Lines power(StatKind kind, long euPerTick, int tier) {
            return line(TipRole.STAT, Tips.powerBody(kind, euPerTick, tier));
        }

        public Lines statText(Component text) {
            return line(TipRole.STAT, text);
        }

        public Lines plain(Component body) {
            return line(TipRole.PLAIN, body);
        }

        public Lines lore(Component body) {
            return line(TipRole.LORE, body);
        }

        public Lines heading(Component body) {
            return line(TipRole.HEADING, body);
        }

        public Lines feature(Component body) {
            return line(TipRole.FEATURE, body);
        }

        public Lines requirement(Component body) {
            return line(TipRole.REQUIREMENT, body);
        }

        public Lines attention(Component body) {
            return line(TipRole.ATTENTION, body);
        }

        public Lines bonus(Component body) {
            return line(TipRole.BONUS, body);
        }

        public Lines penalty(Component body) {
            return line(TipRole.PENALTY, body);
        }

        public Lines warning(Component body) {
            return line(TipRole.WARNING, body);
        }

        public Lines supported(Component body) {
            return line(TipRole.SUPPORTED, body);
        }

        public Lines unsupported(Component body) {
            return line(TipRole.UNSUPPORTED, body);
        }

        public Lines detail(Component body) {
            return line(TipRole.DETAIL, body);
        }

        public Lines formula(Component body) {
            return line(TipRole.FORMULA, body);
        }

        public Lines control(Component body) {
            return line(TipRole.CONTROL, body);
        }

        public Lines leftClick(Component body) {
            return line(TipRole.CONTROL_LEFT, body);
        }

        public Lines rightClick(Component body) {
            return line(TipRole.CONTROL_RIGHT, body);
        }

        public Lines middleClick(Component body) {
            return line(TipRole.CONTROL_MIDDLE, body);
        }
    }
}
