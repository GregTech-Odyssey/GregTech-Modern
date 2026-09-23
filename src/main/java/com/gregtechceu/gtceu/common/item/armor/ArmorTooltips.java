package com.gregtechceu.gtceu.common.item.armor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;
import com.gregtechceu.gtceu.api.item.armor.ArmorComponentItem;
import com.gregtechceu.gtceu.api.item.armor.ArmorLogicSuite;
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
 * GTO: 电力盔甲 tooltip 与提示消息的统一格式。
 * <p>
 * 颜色一律在代码里设置，翻译文本不带 § 格式码（带参数时格式码会在参数之后被重置，导致颜色错乱）。
 * 功能行：名称（白）状态（绿 = 启用 / 被动，黄 = 缺少条件，红 = 关闭）[按键] 耗电（灰）；
 * 长说明只在按住 Shift 时显示。
 */
public final class ArmorTooltips {

    public static final Component SHIFT_HINT = Component.translatable("metaarmor.gto.hint.shift").withStyle(ChatFormatting.DARK_GRAY);

    private ArmorTooltips() {}

    public static Component value(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }

    /**
     * 随状态着色的数值：绿 = 生效，黄 = 缺条件，红 = 关闭
     */
    public static Component value(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }

    /**
     * 数值未按最佳状态生效时附在行尾的原因，例如「 · 电量不足」
     */
    public static Component reason(Component reason, ChatFormatting color) {
        return Component.literal(" · ").append(reason).withStyle(color);
    }

    public static Component section(String key) {
        return Component.literal("◆ ").append(Component.translatable(key)).withStyle(ChatFormatting.GOLD);
    }

    /**
     * 缩进的说明行（灰色），用于防护数值等常驻信息
     */
    public static MutableComponent info(String key, Object... args) {
        return Component.literal(" ").append(Component.translatable(key, args)).withStyle(ChatFormatting.GRAY);
    }

    public static boolean showDetails() {
        return GTCEu.isClientSide() && ArmorTooltipsClient.hasShiftDown();
    }

    /**
     * 按住 Shift 才显示的说明行（深灰）
     */
    public static void addDetail(List<Component> lines, String key, Object... args) {
        if (showDetails()) {
            lines.add(Component.literal("     ").append(Component.translatable(key, args)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * 功能行；cost 为 null 表示不耗电，不显示
     */
    public static void addFeature(List<Component> lines, String key, Component state, @Nullable Component cost) {
        MutableComponent line = Component.literal(" ▸ ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable(key).withStyle(ChatFormatting.WHITE))
                .append("  ").append(state);
        if (cost != null) line.append("  ").append(cost);
        lines.add(line);
    }

    // 状态

    public static MutableComponent state(String key, ChatFormatting color) {
        return Component.translatable(key).withStyle(color);
    }

    public static Component piecePassive() {
        return state("metaarmor.gto.state.piece", ChatFormatting.GREEN);
    }

    /**
     * 单件被动但需要电量才生效
     */
    public static Component piecePassive(boolean hasEnergy) {
        return hasEnergy ? piecePassive() : state("metaarmor.gto.state.no_energy", ChatFormatting.YELLOW);
    }

    /**
     * 全套被动：本件穿在身上且四个部位都满足条件时为绿，否则为黄
     */
    public static Component setPassive(ItemStack stack, Predicate<ItemStack> eachPiece) {
        return isWornInSet(stack, eachPiece) ? state("metaarmor.gto.state.set", ChatFormatting.GREEN) :
                state("metaarmor.gto.state.need_set", ChatFormatting.YELLOW);
    }

    public static Component oxygen(boolean hasOxygen, boolean worn) {
        if (!hasOxygen) return state("metaarmor.gto.state.oxygen_empty", ChatFormatting.YELLOW);
        return worn ? state("metaarmor.gto.state.oxygen_active", ChatFormatting.GREEN) : state("metaarmor.gto.state.oxygen_ready", ChatFormatting.GREEN);
    }

    /**
     * 按键开关：开启为绿、开启但电量不足为黄、关闭为红，后面附按键；未绑定按键时按键提示为黄
     */
    public static Component keyToggle(KeyBind key, boolean enabled, boolean hasEnergy) {
        return toggleState(enabled, hasEnergy).append(keyHint(key, false));
    }

    public static Component keyOrShiftUseToggle(KeyBind key, boolean enabled, boolean hasEnergy) {
        return toggleState(enabled, hasEnergy).append(keyHint(key, true));
    }

    /**
     * 潜行右键开关：开启为绿、开启但电量不足为黄、关闭为红，后面附操作提示
     */
    public static Component shiftUseToggle(boolean enabled, boolean hasEnergy) {
        return toggleState(enabled, hasEnergy)
                .append(Component.literal(" ").append(Component.translatable("metaarmor.gto.shift_use")).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static MutableComponent toggleState(boolean enabled, boolean hasEnergy) {
        if (!enabled) return state("metaarmor.gto.state.off", ChatFormatting.RED);
        return hasEnergy ? state("metaarmor.gto.state.on", ChatFormatting.GREEN) :
                Component.translatable("metaarmor.gto.state.on_but", Component.translatable("metaarmor.gto.reason.no_energy")).withStyle(ChatFormatting.YELLOW);
    }

    private static Component keyHint(KeyBind key, boolean orShiftUse) {
        if (!GTCEu.isClientSide() || ArmorTooltipsClient.isUnbound(key)) {
            return Component.literal(" ").append(Component.translatable("metaarmor.gto.state.unbound")).withStyle(ChatFormatting.YELLOW);
        }
        Component keyName = ArmorTooltipsClient.keyName(key);
        return Component.literal(" ")
                .append(orShiftUse ? Component.translatable("metaarmor.gto.key_or_shift_use", keyName) : Component.translatable("metaarmor.gto.key", keyName))
                .withStyle(ChatFormatting.DARK_GRAY);
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

    /**
     * 与 HazardProperty 的防护判定一致
     */
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

    /**
     * 带喷气背包的胸甲共用的四项按键功能
     */
    public static void addJetpackFeatures(ItemStack stack, IJetpack jetpack, List<Component> features) {
        CompoundTag data = stack.getTag();
        boolean hasEnergy = canUse(stack, jetpack.getEnergyPerUse());
        Component flightCost = ampsPerSecond(JetpackChestHelper.FLIGHT_AMPS);
        addFeature(features, "metaarmor.gto.feature.jetpack",
                keyToggle(KeyBind.JETPACK_ENABLE, JetpackChestHelper.isFlightEnabled(data), hasEnergy), flightCost);
        addDetail(features, "metaarmor.gto.detail.jetpack");
        addFeature(features, "metaarmor.gto.feature.hover",
                keyToggle(KeyBind.ARMOR_HOVER, data != null && data.getBoolean(JetpackChestHelper.HOVER), hasEnergy),
                flightCost);
        addFeature(features, "metaarmor.gto.feature.emergency_hover",
                keyToggle(KeyBind.ARMOR_EMERGENCY_HOVER, JetpackChestHelper.isEmergencyHoverEnabled(data), hasEnergy),
                flightCost);
        addDetail(features, "metaarmor.gto.detail.emergency_hover");
        addFeature(features, "metaarmor.gto.feature.charge_items",
                keyOrShiftUseToggle(KeyBind.ARMOR_CHARGING,
                        data != null && data.getBoolean(JetpackChestHelper.CHARGE), hasCharge(stack)),
                cost("metaarmor.gto.cost.transferred"));
        addDetail(features, "metaarmor.gto.detail.charge_items");
    }

    /**
     * 护腿疾跑倍率：关闭为红，开启为绿并写出倍率，电量不足为黄
     */
    public static void addSpeedFeature(ItemStack stack, ArmorLogicSuite suite, List<Component> features) {
        int level = suite.getSpeedLevel(stack);
        int shownLevel = Math.max(level, 1);
        MutableComponent state;
        if (level <= 0) {
            state = state("metaarmor.gto.state.off", ChatFormatting.RED);
        } else if (canUse(stack, suite.getSpeedCost(level))) {
            state = Component.translatable("metaarmor.gto.state.speed", multiplier(level)).withStyle(ChatFormatting.GREEN);
        } else {
            state = Component.translatable("metaarmor.gto.state.speed_but", multiplier(level), Component.translatable("metaarmor.gto.reason.no_energy")).withStyle(ChatFormatting.YELLOW);
        }
        addFeature(features, "metaarmor.gto.feature.speed_boost", state.append(keyHint(KeyBind.ARMOR_SPEED, false)),
                ampsPerSecond(ArmorLogicSuite.SPEED_AMPS_PER_LEVEL * shownLevel));
        if (showDetails()) {
            MutableComponent levels = Component.empty();
            for (int i = 1; i <= suite.getMaxSpeedLevel(); i++) {
                if (i > 1) levels.append(Component.translatable("metaarmor.gto.separator"));
                levels.append(Component.translatable("metaarmor.gto.detail.speed_level", multiplier(i),
                        amps(ArmorLogicSuite.SPEED_AMPS_PER_LEVEL * i)));
            }
            addDetail(features, "metaarmor.gto.detail.speed_levels", levels);
        }
    }

    public static String multiplier(int level) {
        return amps(ArmorLogicSuite.SPEED_MULTIPLIERS[level]);
    }

    // 耗电：持续耗电以 A/s 标示，一次性耗电为 n/3600 A·h，均按装备自身电压计

    public static Component cost(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GRAY);
    }

    public static Component ampsPerSecond(double amps) {
        return Component.translatable("metaarmor.gto.cost.amps_per_second", amps(amps)).withStyle(ChatFormatting.GRAY);
    }

    /**
     * 一次性耗电 n/3600 A·h；kind 为 per_damage / per_block / per_use / per_jump
     */
    public static Component ampHours(String key, int parts) {
        return Component.translatable(key, parts).withStyle(ChatFormatting.GRAY);
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
        if (!enabled) state = Component.translatable("metaarmor.gto.toggle.off").withStyle(ChatFormatting.RED);
        else if (effective) state = Component.translatable("metaarmor.gto.toggle.on").withStyle(ChatFormatting.GREEN);
        else state = Component.translatable("metaarmor.gto.toggle.on_ineffective", Component.translatable("metaarmor.gto.reason.no_energy")).withStyle(ChatFormatting.YELLOW);
        return Component.empty().append(stack.getDisplayName()).append(" ")
                .append(Component.translatable(nameKey).withStyle(ChatFormatting.WHITE)).append(" ").append(state);
    }

    /**
     * [装备名] 疾跑倍率 已关闭 / 1.8 倍；电量不足时说明暂不生效
     */
    public static Component speedMessage(ItemStack stack, int level, boolean effective) {
        MutableComponent state;
        if (level <= 0) state = Component.translatable("metaarmor.gto.toggle.off").withStyle(ChatFormatting.RED);
        else if (effective) state = Component.translatable("metaarmor.gto.state.speed", multiplier(level)).withStyle(ChatFormatting.GREEN);
        else state = Component.translatable("metaarmor.gto.toggle.speed_ineffective", multiplier(level), Component.translatable("metaarmor.gto.reason.no_energy"))
                .withStyle(ChatFormatting.YELLOW);
        return Component.empty().append(stack.getDisplayName()).append(" ")
                .append(Component.translatable("metaarmor.gto.name.speed_boost").withStyle(ChatFormatting.WHITE)).append(" ").append(state);
    }

    public static Component emergencyHoverMessage(ItemStack stack) {
        return Component.empty().append(stack.getDisplayName()).append(" ")
                .append(Component.translatable("metaarmor.gto.toggle.emergency_triggered").withStyle(ChatFormatting.YELLOW));
    }
}
