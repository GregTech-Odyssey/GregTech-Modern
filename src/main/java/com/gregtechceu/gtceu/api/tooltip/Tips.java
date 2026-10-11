package com.gregtechceu.gtceu.api.tooltip;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 单行提示的静态工厂：用于直接往 tooltips(Component...) 或提示列表里放带角色的行。
 */
public final class Tips {

    private Tips() {}

    public static MutableComponent line(TipRole role, Component body) {
        return TipContents.line(role, body);
    }

    public static MutableComponent heading(Component body) {
        return line(TipRole.HEADING, body);
    }

    public static MutableComponent summary(Component body) {
        return line(TipRole.SUMMARY, body);
    }

    public static MutableComponent plain(Component body) {
        return line(TipRole.PLAIN, body);
    }

    public static MutableComponent feature(Component body) {
        return line(TipRole.FEATURE, body);
    }

    public static MutableComponent requirement(Component body) {
        return line(TipRole.REQUIREMENT, body);
    }

    public static MutableComponent attention(Component body) {
        return line(TipRole.ATTENTION, body);
    }

    public static MutableComponent bonus(Component body) {
        return line(TipRole.BONUS, body);
    }

    public static MutableComponent penalty(Component body) {
        return line(TipRole.PENALTY, body);
    }

    public static MutableComponent warning(Component body) {
        return line(TipRole.WARNING, body);
    }

    public static MutableComponent supported(Component body) {
        return line(TipRole.SUPPORTED, body);
    }

    public static MutableComponent unsupported(Component body) {
        return line(TipRole.UNSUPPORTED, body);
    }

    public static MutableComponent note(Component body) {
        return line(TipRole.NOTE, body);
    }

    public static MutableComponent detail(Component body) {
        return line(TipRole.DETAIL, body);
    }

    public static MutableComponent formula(Component body) {
        return line(TipRole.FORMULA, body);
    }

    public static MutableComponent control(Component body) {
        return line(TipRole.CONTROL, body);
    }

    public static MutableComponent leftClick(Component body) {
        return line(TipRole.CONTROL_LEFT, body);
    }

    public static MutableComponent rightClick(Component body) {
        return line(TipRole.CONTROL_RIGHT, body);
    }

    public static MutableComponent middleClick(Component body) {
        return line(TipRole.CONTROL_MIDDLE, body);
    }

    public static MutableComponent lore(Component body) {
        return line(TipRole.LORE, body);
    }

    public static MutableComponent stat(Component fullText) {
        return line(TipRole.STAT, fullText);
    }

    public static MutableComponent stat(StatKind kind, Object... args) {
        return line(TipRole.STAT, kind.line(args));
    }

    public static MutableComponent charge(long current, long max, int tier) {
        MutableComponent value;
        if (max <= 0) {
            value = Component.translatable("gtceu.tip.unit.charge_none");
        } else if (GTUtil.isShiftDown()) {
            value = Component.translatable("gtceu.tip.unit.charge_exact", FormattingUtil.formatNumbers(current),
                    FormattingUtil.formatNumbers(max));
        } else {
            boolean hours = useHours(max, tier);
            value = Component.translatable(hours ? "gtceu.tip.unit.charge_hours" : "gtceu.tip.unit.charge_seconds",
                    ampTime(current, tier, hours), ampTime(max, tier, hours));
        }
        float ratio = max <= 0 ? 0 : Math.min(1f, (float) current / max);
        boolean depleted = TipTheme.energyDepleted(current, max);
        return TipContents.meter(StatKind.CHARGE.line(withTier(value, tier)), ratio,
                depleted ? TipTheme.ENERGY_BAR_DEPLETED_LEFT : TipTheme.ENERGY_BAR_LEFT,
                depleted ? TipTheme.ENERGY_BAR_DEPLETED_RIGHT : TipTheme.ENERGY_BAR_RIGHT);
    }

    public static MutableComponent usage(StatKind kind, long used, long total) {
        long shown = Math.max(0, used);
        if (total <= 0 || total >= Long.MAX_VALUE / 2) {
            return stat(kind, FormattingUtil.formatNumbers(shown));
        }
        Component value = Component.translatable("gtceu.tip.unit.durability", FormattingUtil.formatNumbers(shown),
                FormattingUtil.formatNumbers(total));
        float ratio = Math.min(1f, (float) shown / total);
        int left = shown >= total ? TipTheme.ENERGY_BAR_DEPLETED_LEFT : ratio >= 0.75f ? TipTheme.USAGE_WARN_LEFT : TipTheme.ENERGY_BAR_LEFT;
        int right = shown >= total ? TipTheme.ENERGY_BAR_DEPLETED_RIGHT : ratio >= 0.75f ? TipTheme.USAGE_WARN_RIGHT : TipTheme.ENERGY_BAR_RIGHT;
        return TipContents.meter(kind.line(value), ratio, left, right);
    }

    public static MutableComponent progress(Component body, float ratio) {
        return TipContents.meter(body, Math.max(0, Math.min(1, ratio)), TipTheme.ENERGY_BAR_LEFT, TipTheme.ENERGY_BAR_RIGHT);
    }

    public static MutableComponent segments(Component body, float[] ratios, int[] colors) {
        return TipContents.segments(body, ratios, colors);
    }

    public static MutableComponent durability(long remaining, long max) {
        long shown = Math.max(0, Math.min(remaining, max));
        Component value = Component.translatable("gtceu.tip.unit.durability", FormattingUtil.formatNumbers(shown),
                FormattingUtil.formatNumbers(max));
        float ratio = max <= 0 ? 0 : (float) shown / max;
        boolean depleted = max > 0 && shown * 4 <= max;
        return TipContents.meter(StatKind.DURABILITY.line(value), ratio,
                depleted ? TipTheme.ENERGY_BAR_DEPLETED_LEFT : TipTheme.DURABILITY_BAR_LEFT,
                depleted ? TipTheme.ENERGY_BAR_DEPLETED_RIGHT : TipTheme.DURABILITY_BAR_RIGHT);
    }

    private static MutableComponent withTier(MutableComponent value, int tier) {
        return value.withStyle(s -> s.withColor(TipTheme.VALUE)).append(Component.translatable("gtceu.tip.unit.tier", GTValues.VNF[tier]));
    }

    public static MutableComponent energy(StatKind kind, long eu, int tier) {
        return stat(energyBody(kind, eu, tier));
    }

    static MutableComponent energyBody(StatKind kind, long eu, int tier) {
        return dynamic(() -> {
            MutableComponent value;
            if (GTUtil.isShiftDown()) {
                value = Component.translatable("gtceu.tip.unit.energy_exact", FormattingUtil.formatNumbers(eu));
            } else {
                boolean hours = useHours(eu, tier);
                value = Component.translatable(hours ? "gtceu.tip.unit.energy_hours" : "gtceu.tip.unit.energy_seconds",
                        ampTime(eu, tier, hours));
            }
            return kind.line(withTier(value, tier));
        });
    }

    public static MutableComponent power(StatKind kind, long euPerTick, int tier) {
        return stat(powerBody(kind, euPerTick, tier));
    }

    static MutableComponent powerBody(StatKind kind, long euPerTick, int tier) {
        return dynamic(() -> {
            MutableComponent value;
            if (GTUtil.isShiftDown()) {
                value = Component.translatable("gtceu.tip.unit.power_exact", FormattingUtil.formatNumbers(euPerTick));
            } else {
                double amps = (double) euPerTick / GTValues.VEX[tier];
                String text = amps == Math.rint(amps) ? FormattingUtil.formatNumbers((long) amps) : String.format("%,.1f", amps);
                value = Component.translatable("gtceu.tip.unit.power", text);
            }
            return kind.line(withTier(value, tier));
        });
    }

    private static boolean useHours(long eu, int tier) {
        return eu / (GTValues.V[tier] * 20D) >= 3600;
    }

    private static String ampTime(long eu, int tier, boolean hours) {
        double seconds = eu / (GTValues.V[tier] * 20D);
        return hours ? String.format("%,.1f", seconds / 3600) : FormattingUtil.formatNumbers((long) seconds);
    }

    public static MutableComponent dynamic(Supplier<? extends Component> text) {
        return MutableComponent.create(new DynamicContents(text));
    }

    private record DynamicContents(Supplier<? extends Component> text) implements ComponentContents {

        @Override
        public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style style) {
            return text.get().visit(consumer, style);
        }

        @Override
        public <T> Optional<T> visit(FormattedText.ContentConsumer<T> consumer) {
            return text.get().visit(consumer);
        }
    }
}
