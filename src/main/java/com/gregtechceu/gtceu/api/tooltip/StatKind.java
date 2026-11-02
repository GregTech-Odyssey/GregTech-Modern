package com.gregtechceu.gtceu.api.tooltip;

import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.jetbrains.annotations.Nullable;

/**
 * 属性行的种类：标签、领域色与单位格式，渲染为原生的"标签：值"。
 * <b>仅用于物品/方块 tooltip（悬停提示框），不是通用的文字渲染设施</b>：机器界面、说明页、显示行、聊天一律用普通
 * {@code Component} 与 uipro 文本组件，不要拿这里的行或规格去拼。
 */
public record StatKind(String id, String labelKey, int color, @Nullable String unitKey, Component label) {

    public StatKind(String id, String labelKey, int color, @Nullable String unitKey) {
        this(id, labelKey, color, unitKey, Component.translatable(labelKey));
    }

    private static final String STAT_TEMPLATE = "gtceu.tip.stat";

    public static final StatKind VOLTAGE_IN = new StatKind("voltage_in", "gtceu.tip.stat.voltage_in", TipTheme.ENERGY_VOLTAGE, null);
    public static final StatKind VOLTAGE_OUT = new StatKind("voltage_out", "gtceu.tip.stat.voltage_out", TipTheme.ENERGY_VOLTAGE, null);
    public static final StatKind AMPERAGE_IN = new StatKind("amperage_in", "gtceu.tip.stat.amperage_in", TipTheme.ENERGY_AMPERAGE, "gtceu.electric.amps");
    public static final StatKind AMPERAGE_OUT = new StatKind("amperage_out", "gtceu.tip.stat.amperage_out", TipTheme.ENERGY_AMPERAGE, "gtceu.electric.amps");
    public static final StatKind ENERGY_CAPACITY = new StatKind("energy_capacity", "gtceu.tip.stat.energy_capacity", TipTheme.ENERGY_STORAGE, "gtceu.electric.energy_exact");
    public static final StatKind FLUID_CAPACITY = new StatKind("fluid_capacity", "gtceu.tip.stat.fluid_capacity", TipTheme.FLUID, "gtceu.tip.unit.mb");
    public static final StatKind ITEM_SLOTS = new StatKind("item_slots", "gtceu.tip.stat.item_slots", TipTheme.ITEM, null);
    public static final StatKind PATTERN_SLOTS = new StatKind("pattern_slots", "gtceu.tip.stat.pattern_slots", TipTheme.ITEM, null);
    public static final StatKind PARALLEL = new StatKind("parallel", "gtceu.tip.stat.parallel", TipTheme.PARALLEL, null);
    public static final StatKind TIER = new StatKind("tier", "gtceu.tip.stat.tier", TipTheme.TIER, null);
    public static final StatKind EFFICIENCY = new StatKind("efficiency", "gtceu.tip.stat.efficiency", TipTheme.EFFICIENCY, "gtceu.tip.unit.percent");
    public static final StatKind BATTERY_CAPACITY = new StatKind("battery_capacity", "gtceu.tip.stat.battery_capacity", TipTheme.ENERGY_STORAGE, null);
    public static final StatKind ENERGY_TRANSFER = new StatKind("energy_transfer", "gtceu.tip.stat.energy_transfer", TipTheme.ENERGY_AMPERAGE, null);
    public static final StatKind BATTERY = new StatKind("battery", "gtceu.tip.stat.battery", TipTheme.ENERGY_STORAGE, null);
    public static final StatKind DURABILITY = new StatKind("durability", "gtceu.tip.stat.durability", TipTheme.POSITIVE, null);
    public static final StatKind STORAGE_BYTES = new StatKind("storage_bytes", "gtceu.tip.stat.storage_bytes", TipTheme.ITEM, null);
    public static final StatKind STORAGE_TYPES = new StatKind("storage_types", "gtceu.tip.stat.storage_types", TipTheme.ITEM, null);
    public static final StatKind CHARGE = new StatKind("charge", "gtceu.tip.stat.charge", TipTheme.ENERGY_STORAGE, null);
    public static final StatKind MANA = new StatKind("mana", "gtceu.tip.stat.mana", TipTheme.MANA, null);
    public static final StatKind RECIPE_TYPE = new StatKind("recipe_type", "gtceu.tip.stat.recipe_type", TipTheme.PRODUCTION, null);

    public MutableComponent value(Object... args) {
        MutableComponent value;
        if (unitKey != null) value = Component.translatable(unitKey, args);
        else if (args.length == 0) value = Component.empty();
        else if (args[0] instanceof Component c) value = c.copy();
        else value = Component.literal(String.valueOf(args[0]));
        return value.withStyle(s -> s.withColor(TipTheme.VALUE));
    }

    public MutableComponent line(Object... args) {
        return line(value(args));
    }

    public MutableComponent line(Component value) {
        Component shown = Component.empty().withStyle(s -> s.withColor(TipTheme.VALUE)).append(value);
        return Component.translatable(STAT_TEMPLATE, label, shown)
                .withStyle(s -> s.withColor(color));
    }

    public static Object[] voltage(long voltage, int tier) {
        return new Object[] { ElectricText.voltage(voltage, tier) };
    }

    public static String number(long value) {
        return FormattingUtil.formatNumbers(value);
    }
}
