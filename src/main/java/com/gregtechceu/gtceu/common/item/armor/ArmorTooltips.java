package com.gregtechceu.gtceu.common.item.armor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;
import com.gregtechceu.gtceu.api.item.armor.ArmorComponentItem;
import com.gregtechceu.gtceu.api.item.armor.ArmorLogicSuite;
import com.gregtechceu.gtceu.api.tooltip.ElectricText;
import com.gregtechceu.gtceu.api.tooltip.TipRole;
import com.gregtechceu.gtceu.api.tooltip.Tips;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.gregtechceu.gtceu.utils.input.KeyBind;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * GTO: 电力盔甲与能量剑的提示行：分区、数值、被动能力、按键开关与 Shift 说明，样式全部交给 {@link Tips}。
 */
public final class ArmorTooltips {

    public static final Component SHIFT_HINT = Tips.line(TipRole.FOOTER, Component.translatable("gtceu.tooltip.hold_shift"));
    private static final Component NO_ENERGY_REASON = Component.translatable("metaarmor.gto.reason.no_energy");
    private static final Component SPEED_BOOST_NAME = Component.translatable("metaarmor.gto.name.speed_boost");
    private static final Component TOGGLE_OFF = Component.translatable("metaarmor.gto.toggle.off");
    private static final Component TOGGLE_ON = Component.translatable("metaarmor.gto.toggle.on");
    private static final Component EMERGENCY_TRIGGERED = Component.translatable("metaarmor.gto.toggle.emergency_triggered");
    private static final Component SEPARATOR = Component.translatable("metaarmor.gto.separator");
    private static final String TOGGLE_ON_INEFFECTIVE = "metaarmor.gto.toggle.on_ineffective";
    private static final String TOGGLE_SPEED_INEFFECTIVE = "metaarmor.gto.toggle.speed_ineffective";
    private static final String VALUE_MULTIPLIER = "metaarmor.gto.value.multiplier";
    private static final String DETAIL_SPEED_BOOST = "metaarmor.gto.detail.speed_boost";

    private ArmorTooltips() {}

    public static Component value(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    public static Component heading(String key) {
        return Tips.heading(Component.translatable(key));
    }

    public static MutableComponent stat(String key, Object... args) {
        return Tips.stat(Component.translatable(key, args));
    }

    public static MutableComponent passive(String key, Object... args) {
        return Tips.feature(Component.translatable(key, args));
    }

    public static Component noEnergy() {
        return Tips.attention(Component.translatable("metaarmor.gto.no_energy"));
    }

    public static boolean showDetails() {
        return GTCEu.isClientSide() && ArmorTooltipsClient.hasShiftDown();
    }

    public static void addDetail(List<Component> lines, String key, Object... args) {
        if (showDetails()) lines.add(Tips.detail(Component.translatable(key, args)));
    }

    public static Component hotkey(KeyBind key, String nameKey, boolean enabled) {
        return Tips.hotkey(key, Tips.toggle(Component.translatable(nameKey), enabled));
    }

    public static Component shiftUse(String nameKey, boolean enabled) {
        return Tips.rightClick(Tips.keyAction(Component.translatable("metaarmor.gto.shift_use"),
                Tips.toggle(Component.translatable(nameKey), enabled)));
    }

    public static Component ampsPerSecond(double amps) {
        return ElectricText.ampsPerSecond(amps);
    }

    public static Component ampSeconds(double amps) {
        return ElectricText.ampSeconds(amps);
    }

    public static Component value(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }

    public static Component section(String key) {
        return heading(key);
    }

    public static MutableComponent info(String key, Object... args) {
        return stat(key, args);
    }

    public static void addFeature(List<Component> lines, String key, Component state, @Nullable Component cost) {
        MutableComponent line = Component.translatable(key).withStyle(ChatFormatting.WHITE).append("  ").append(state);
        if (cost != null) line.append("  ").append(cost);
        lines.add(Tips.feature(line));
    }

    public static MutableComponent state(String key, ChatFormatting color) {
        return Component.translatable(key).withStyle(color);
    }

    public static Component piecePassive() {
        return state("metaarmor.gto.state.piece", ChatFormatting.GREEN);
    }

    public static Component piecePassive(boolean hasEnergy) {
        return hasEnergy ? piecePassive() : state("metaarmor.gto.state.no_energy", ChatFormatting.YELLOW);
    }

    public static Component setPassive(ItemStack stack, Predicate<ItemStack> eachPiece) {
        return isWornInSet(stack, eachPiece) ? state("metaarmor.gto.state.set", ChatFormatting.GREEN) :
                state("metaarmor.gto.state.need_set", ChatFormatting.YELLOW);
    }

    public static Component oxygen(boolean hasOxygen, boolean worn) {
        if (!hasOxygen) return state("metaarmor.gto.state.oxygen_empty", ChatFormatting.YELLOW);
        return worn ? state("metaarmor.gto.state.oxygen_active", ChatFormatting.GREEN) : state("metaarmor.gto.state.oxygen_ready", ChatFormatting.GREEN);
    }

    // 条件判断

    public static boolean isWornInSet(ItemStack stack, Predicate<ItemStack> eachPiece) {
        if (!isWorn(stack)) return false;
        List<ItemStack> armor = ArmorTooltipsClient.wornArmor();
        for (int i = 0; i < armor.size(); i++) {
            ItemStack piece = armor.get(i);
            if (piece.isEmpty() || !eachPiece.test(piece)) return false;
        }
        return true;
    }

    public static boolean isWorn(ItemStack stack) {
        if (!GTCEu.isClientSide()) return false;
        List<ItemStack> armor = ArmorTooltipsClient.wornArmor();
        for (int i = 0; i < armor.size(); i++) {
            if (armor.get(i) == stack) return true;
        }
        return false;
    }

    public static boolean isPPE(ItemStack stack) {
        return (stack.getItem() instanceof ArmorComponentItem armor && armor.getArmorLogic().isPPE()) ||
                stack.is(CustomTags.PPE_ARMOR);
    }

    public static boolean canUse(ItemStack stack, long amount) {
        IElectricItem item = GTCapabilityHelper.getElectricItem(stack);
        return item != null && item.canUse(amount);
    }

    public static boolean hasCharge(ItemStack stack) {
        IElectricItem item = GTCapabilityHelper.getElectricItem(stack);
        return item != null && item.getCharge() > 0;
    }

    public static void addJetpackControls(ItemStack stack, List<Component> controls) {
        CompoundTag data = stack.getTag();
        controls.add(hotkey(KeyBind.JETPACK_ENABLE, "metaarmor.gto.name.jetpack", JetpackChestHelper.isFlightEnabled(data)));
        addDetail(controls, "metaarmor.gto.detail.jetpack", ampsPerSecond(JetpackChestHelper.FLIGHT_AMPS));
        controls.add(hotkey(KeyBind.ARMOR_HOVER, "metaarmor.gto.name.hover", data != null && data.getBoolean(JetpackChestHelper.HOVER)));
        controls.add(hotkey(KeyBind.ARMOR_EMERGENCY_HOVER, "metaarmor.gto.name.emergency_hover",
                JetpackChestHelper.isEmergencyHoverEnabled(data)));
        addDetail(controls, "metaarmor.gto.detail.emergency_hover");
        controls.add(hotkey(KeyBind.ARMOR_CHARGING, "metaarmor.gto.name.charge_items", data != null && data.getBoolean(JetpackChestHelper.CHARGE)));
        addDetail(controls, "metaarmor.gto.detail.charge_items");
    }

    public static void addSpeedControl(ItemStack stack, ArmorLogicSuite suite, List<Component> controls) {
        int level = suite.getSpeedLevel(stack);
        Component state = level <= 0 ? Tips.onOff(false) : Component.translatable(VALUE_MULTIPLIER, multiplier(level));
        controls.add(Tips.hotkey(KeyBind.ARMOR_SPEED, Tips.toggle(SPEED_BOOST_NAME, state)));
        if (showDetails()) {
            MutableComponent levels = Component.empty();
            for (int i = 1; i <= suite.getMaxSpeedLevel(); i++) {
                if (i > 1) levels.append(SEPARATOR);
                levels.append(multiplier(i));
            }
            addDetail(controls, DETAIL_SPEED_BOOST, levels, ampsPerSecond(ArmorLogicSuite.SPEED_AMPS_PER_LEVEL));
        }
    }

    public static String multiplier(int level) {
        return amps(ArmorLogicSuite.SPEED_MULTIPLIERS[level]);
    }

    /**
     * 最多两位小数，去掉末尾的 0（tooltip 里避免 String.format）
     */
    public static String amps(double value) {
        long hundredths = Math.round(value * 100);
        long whole = hundredths / 100;
        long fraction = hundredths % 100;
        if (fraction == 0) return Long.toString(whole);
        StringBuilder sb = new StringBuilder().append(whole).append('.');
        if (fraction % 10 == 0) return sb.append(fraction / 10).toString();
        if (fraction < 10) sb.append('0');
        return sb.append(fraction).toString();
    }

    // 聊天提示

    /**
     * [装备名] 功能 已开启 / 已关闭；开启但条件不足时说明暂不生效
     */
    public static Component toggleMessage(ItemStack stack, String nameKey, boolean enabled, boolean effective) {
        MutableComponent state;
        if (!enabled) state = TOGGLE_OFF.copy().withStyle(ChatFormatting.RED);
        else if (effective) state = TOGGLE_ON.copy().withStyle(ChatFormatting.GREEN);
        else state = Component.translatable(TOGGLE_ON_INEFFECTIVE, NO_ENERGY_REASON).withStyle(ChatFormatting.YELLOW);
        return Component.empty().append(stack.getDisplayName()).append(" ")
                .append(Component.translatable(nameKey).withStyle(ChatFormatting.WHITE)).append(" ").append(state);
    }

    /**
     * [装备名] 疾跑倍率 已关闭 / 1.8 倍；电量不足时说明暂不生效
     */
    public static Component speedMessage(ItemStack stack, int level, boolean effective) {
        MutableComponent state;
        if (level <= 0) state = TOGGLE_OFF.copy().withStyle(ChatFormatting.RED);
        else if (effective) state = Component.translatable(VALUE_MULTIPLIER, multiplier(level)).withStyle(ChatFormatting.GREEN);
        else state = Component.translatable(TOGGLE_SPEED_INEFFECTIVE, multiplier(level), NO_ENERGY_REASON)
                .withStyle(ChatFormatting.YELLOW);
        return Component.empty().append(stack.getDisplayName()).append(" ")
                .append(SPEED_BOOST_NAME.copy().withStyle(ChatFormatting.WHITE)).append(" ").append(state);
    }

    public static Component emergencyHoverMessage(ItemStack stack) {
        return Component.empty().append(stack.getDisplayName()).append(" ")
                .append(EMERGENCY_TRIGGERED.copy().withStyle(ChatFormatting.YELLOW));
    }
}
