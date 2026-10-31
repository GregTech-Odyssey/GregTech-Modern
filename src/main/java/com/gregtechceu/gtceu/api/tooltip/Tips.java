package com.gregtechceu.gtceu.api.tooltip;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.client.tooltip.TipKeyText;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.input.KeyBind;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 单行提示的静态工厂：往物品/方块的 tooltip 列表里放带角色的行。
 * <b>仅用于物品/方块 tooltip（悬停提示框），不是通用的文字渲染设施</b>：机器界面、说明页、显示行、聊天一律用普通
 * {@code Component} 与 uipro 文本组件，不要拿这里的行或规格去拼。
 */
public final class Tips {

    private static final String KEY_ACTION = "gtceu.tip.key_action";
    private static final String TOGGLE = "gtceu.tip.toggle";
    private static final String DURABILITY_UNIT = "gtceu.tip.unit.durability";
    private static final Component KEY_UNBOUND = Component.translatable("gtceu.tip.key.unbound").withStyle(Style.EMPTY.withColor(TipTheme.ATTENTION));
    private static final Component STATE_ON = Component.translatable("gtceu.tip.state.on").withStyle(Style.EMPTY.withColor(TipTheme.VALUE));
    private static final Component STATE_OFF = Component.translatable("gtceu.tip.state.off").withStyle(Style.EMPTY.withColor(TipTheme.NOTE));
    private static final Component CHARGE_NONE = Component.translatable("gtceu.tip.unit.charge_none");

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

    public static MutableComponent hotkey(KeyBind key, Component action) {
        return control(keyAction(keyName(key), action));
    }

    public static MutableComponent keyAction(Component key, Component action) {
        return Component.translatable(KEY_ACTION, key, action);
    }

    public static Component keyName(KeyBind key) {
        return GTCEu.isClientSide() ? TipKeyText.of(key) : KEY_UNBOUND;
    }

    public static MutableComponent toggle(Component name, boolean on) {
        return toggle(name, onOff(on));
    }

    public static MutableComponent toggle(Component name, Component state) {
        return Component.translatable(TOGGLE, name, state);
    }

    public static MutableComponent onOff(boolean on) {
        return (on ? STATE_ON : STATE_OFF).copy();
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
            value = CHARGE_NONE.copy();
        } else if (GTUtil.isShiftDown()) {
            value = ElectricText.chargeExact(current, max);
        } else {
            value = ElectricText.charge(current, max, tier);
        }
        float ratio = max <= 0 ? 0 : Math.min(1f, (float) current / max);
        boolean depleted = TipTheme.energyDepleted(current, max);
        return TipContents.meter(StatKind.CHARGE.line(value.withStyle(s -> s.withColor(TipTheme.VALUE))), ratio,
                depleted ? TipTheme.ENERGY_BAR_DEPLETED_LEFT : TipTheme.ENERGY_BAR_LEFT,
                depleted ? TipTheme.ENERGY_BAR_DEPLETED_RIGHT : TipTheme.ENERGY_BAR_RIGHT);
    }

    public static MutableComponent usage(StatKind kind, long used, long total) {
        long shown = Math.max(0, used);
        if (total <= 0 || total >= Long.MAX_VALUE / 2) {
            return stat(kind, FormattingUtil.formatNumbers(shown));
        }
        Component value = Component.translatable(DURABILITY_UNIT, FormattingUtil.formatNumbers(shown),
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

    public static MutableComponent mana(long current, long max) {
        long shown = Math.max(0, Math.min(current, max));
        Component value = Component.translatable(DURABILITY_UNIT, FormattingUtil.formatNumbers(shown),
                FormattingUtil.formatNumbers(max));
        float ratio = max <= 0 ? 0 : (float) shown / max;
        return TipContents.meter(StatKind.MANA.line(value), ratio, TipTheme.MANA_BAR_LEFT, TipTheme.MANA_BAR_RIGHT);
    }

    public static MutableComponent durability(long remaining, long max) {
        long shown = Math.max(0, Math.min(remaining, max));
        Component value = Component.translatable(DURABILITY_UNIT, FormattingUtil.formatNumbers(shown),
                FormattingUtil.formatNumbers(max));
        float ratio = max <= 0 ? 0 : (float) shown / max;
        boolean depleted = max > 0 && shown * 4 <= max;
        return TipContents.meter(StatKind.DURABILITY.line(value), ratio,
                depleted ? TipTheme.ENERGY_BAR_DEPLETED_LEFT : TipTheme.DURABILITY_BAR_LEFT,
                depleted ? TipTheme.ENERGY_BAR_DEPLETED_RIGHT : TipTheme.DURABILITY_BAR_RIGHT);
    }

    public static MutableComponent energy(StatKind kind, long eu, int tier) {
        return stat(energyBody(kind, eu, tier));
    }

    static MutableComponent energyBody(StatKind kind, long eu, int tier) {
        return dynamic(() -> kind.line(GTUtil.isShiftDown() ? ElectricText.energyExact(eu) : ElectricText.energy(eu, tier)));
    }

    public static MutableComponent power(StatKind kind, long euPerTick, int tier) {
        return stat(powerBody(kind, euPerTick, tier));
    }

    static MutableComponent powerBody(StatKind kind, long euPerTick, int tier) {
        return dynamic(() -> kind.line(GTUtil.isShiftDown() ? ElectricText.power(euPerTick) : ElectricText.flowOf(euPerTick, tier)));
    }

    public static MutableComponent dynamic(Supplier<? extends Component> text) {
        return MutableComponent.create(new DynamicContents(text));
    }

    public static Component resolveDynamic(Component text) {
        return hasDynamic(text) ? resolved(text) : text;
    }

    private static boolean hasDynamic(Component text) {
        var contents = text.getContents();
        if (contents instanceof DynamicContents) return true;
        if (contents instanceof TranslatableContents translatable) {
            for (Object arg : translatable.getArgs()) if (arg instanceof Component c && hasDynamic(c)) return true;
        }
        for (Component sibling : text.getSiblings()) if (hasDynamic(sibling)) return true;
        return false;
    }

    private static MutableComponent resolved(Component text) {
        var contents = text.getContents();
        MutableComponent out;
        if (contents instanceof DynamicContents dynamic) {
            out = Component.empty().append(resolveDynamic(dynamic.text().get()));
        } else if (contents instanceof TranslatableContents translatable) {
            Object[] args = translatable.getArgs().clone();
            for (int i = 0; i < args.length; i++) if (args[i] instanceof Component c) args[i] = resolveDynamic(c);
            out = MutableComponent.create(new TranslatableContents(translatable.getKey(), translatable.getFallback(), args));
        } else {
            out = MutableComponent.create(contents);
        }
        out.setStyle(text.getStyle());
        for (Component sibling : text.getSiblings()) out.append(resolveDynamic(sibling));
        return out;
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
