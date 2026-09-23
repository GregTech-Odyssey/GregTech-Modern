package com.gregtechceu.gtceu.api.item.armor;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;
import com.gregtechceu.gtceu.api.item.component.ElectricStats;
import com.gregtechceu.gtceu.api.item.component.IItemHUDProvider;
import com.gregtechceu.gtceu.common.item.armor.ArmorSuiteFeatures;
import com.gregtechceu.gtceu.common.item.armor.ArmorTooltips;
import com.gregtechceu.gtceu.common.item.armor.GTArmorMaterials;
import com.gregtechceu.gtceu.utils.input.KeyBind;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class ArmorLogicSuite implements IArmorLogic, IItemHUDProvider {

    @Getter
    protected final int energyPerUse;
    protected final int tier;
    protected final long maxCapacity;
    protected final ArmorItem.Type type;
    @Nullable
    private List<Component> staticInfo;
    @Nullable
    private List<Component> staticDetails;

    protected ArmorLogicSuite(int energyPerUse, long maxCapacity, int tier, ArmorItem.Type type) {
        this.energyPerUse = energyPerUse;
        this.maxCapacity = maxCapacity;
        this.tier = tier;
        this.type = type;
    }

    @Override
    public abstract void onArmorTick(Level Level, Player player, ItemStack itemStack);

    @Override
    public int getArmorDisplay(Player player, @NotNull ItemStack armor, EquipmentSlot slot) {
        IElectricItem item = GTCapabilityHelper.getElectricItem(armor);
        if (item == null) return 0;
        return Math.round(getArmorPoints(item.getCharge() >= energyPerUse));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != this.type.getSlot()) return ImmutableMultimap.of();
        IElectricItem item = GTCapabilityHelper.getElectricItem(stack);
        UUID uuid = IArmorLogic.ARMOR_MODIFIER_UUID_PER_TYPE.get(type);
        if (item == null) return ImmutableMultimap.of();
        boolean charged = item.getCharge() >= energyPerUse;
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(Attributes.ARMOR, new AttributeModifier(uuid, "Armor modifier",
                getArmorPoints(charged), AttributeModifier.Operation.ADDITION));
        // GTO: 有电时的额外韧性与击退抗性，与原版材质属性的 UUID 不同，可以叠加
        if (charged && getExtraToughness() > 0) {
            builder.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(uuid, "Armor toughness",
                    getExtraToughness(), AttributeModifier.Operation.ADDITION));
        }
        if (charged && getExtraKnockbackResistance() > 0) {
            builder.put(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(uuid, "Armor knockback resistance",
                    getExtraKnockbackResistance(), AttributeModifier.Operation.ADDITION));
        }
        // GTO: 生命强化，默认开启，按秒扣电
        if (getGrade() > 0 && ArmorSuiteFeatures.isLifeBoostEnabled(stack) &&
                item.canUse(ArmorSuiteFeatures.lifeBoostCostPerSecond(this))) {
            builder.put(Attributes.MAX_HEALTH, new AttributeModifier(uuid, "Armor health boost",
                    getPieceHealthBoost(), AttributeModifier.Operation.ADDITION));
        }
        if (type == ArmorItem.Type.LEGGINGS) {
            int level = getSpeedLevel(stack);
            if (level > 0 && item.canUse(getSpeedCost(level))) {
                builder.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(uuid, "Armor speed multiplier",
                        SPEED_MULTIPLIERS[level] - 1, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
        return builder.build();
    }

    // GTO: 耗电按装备自身电压的安培换算，持续耗电以 A/s 标示（实际每 tick 扣 1/20），一次性耗电为 n/3600 A·h

    /**
     * 持续耗电 ampsPerSecond A/s 时，每 tick 应扣的 EU
     */
    public long ampsPerSecond(double ampsPerSecond) {
        return Math.round(ampsPerSecond * GTValues.V[tier] / 20);
    }

    /**
     * n/3600 A·h（即 n 安秒）对应的 EU
     */
    public long ampHourParts(double n) {
        return Math.round(n * 20 * GTValues.V[tier]);
    }

    public int getTier() {
        return tier;
    }

    /**
     * GTO: 套装等级，纳米肌体 I = 1、进阶 II = 2、夸克高科 III = 3、进阶 IV = 4；0 表示不是这四套
     */
    public int getGrade() {
        return 0;
    }

    /**
     * GTO: 生命强化，整套 +10 × 等级，每件分得 1/4
     */
    public float getPieceHealthBoost() {
        return getGrade() * 10 / 4.0F;
    }

    // GTO: 护腿疾跑倍率。I~IV 分别可切换 1~4 档，每档 1 A/s，只在移动时耗电

    public static final double[] SPEED_MULTIPLIERS = { 1.0, 1.8, 3.0, 4.5, 6.0 };
    public static final double SPEED_AMPS_PER_LEVEL = 1.0;
    public static final String SPEED_LEVEL = "speedLevel";

    /**
     * 护腿可切换的最高倍率档位，0 表示没有该功能
     */
    public int getMaxSpeedLevel() {
        return 0;
    }

    public int getSpeedLevel(ItemStack stack) {
        CompoundTag data = stack.getTag();
        if (data == null) return 0;
        return Math.min(data.getInt(SPEED_LEVEL), getMaxSpeedLevel());
    }

    public long getSpeedCost(int level) {
        return ampsPerSecond(SPEED_AMPS_PER_LEVEL * level);
    }

    /**
     * 当前护腿倍率；喷气背包飞行的水平速度也乘以它
     */
    public static double getLeggingsSpeedMultiplier(Player player) {
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (!(legs.getItem() instanceof ArmorComponentItem armor) ||
                !(armor.getArmorLogic() instanceof ArmorLogicSuite suite) || suite.type != ArmorItem.Type.LEGGINGS) {
            return 1.0;
        }
        int level = suite.getSpeedLevel(legs);
        if (level <= 0) return 1.0;
        IElectricItem item = GTCapabilityHelper.getElectricItem(legs);
        return item != null && item.canUse(suite.getSpeedCost(level)) ? SPEED_MULTIPLIERS[level] : 1.0;
    }

    /**
     * 护腿每 tick 调用：按键循环切换档位（只在服务端按下沿切换），移动时按档位扣电
     */
    protected void tickSpeedLeggings(Level world, Player player, ItemStack stack) {
        if (world.isClientSide || getMaxSpeedLevel() <= 0) return;
        IElectricItem item = GTCapabilityHelper.getElectricItem(stack);
        if (item == null) return;
        int level = getSpeedLevel(stack);
        if (KeyBind.ARMOR_SPEED.consumePress(player)) {
            level = level >= getMaxSpeedLevel() ? 0 : level + 1;
            stack.getOrCreateTag().putInt(SPEED_LEVEL, level);
            player.displayClientMessage(ArmorTooltips.speedMessage(stack, level, item.canUse(getSpeedCost(level))),
                    false);
        }
        if (level > 0 && isMoving(player)) {
            long cost = getSpeedCost(level);
            if (item.canUse(cost)) item.discharge(cost, item.getTier(), true, false, false);
        }
    }

    private static boolean isMoving(Player player) {
        return KeyBind.VANILLA_FORWARD.isKeyDown(player) || KeyBind.VANILLA_BACKWARD.isKeyDown(player) ||
                KeyBind.VANILLA_LEFT.isKeyDown(player) || KeyBind.VANILLA_RIGHT.isKeyDown(player);
    }

    /**
     * GTO: 有电时满额护甲，没电时 2/5（GTM 原为 1/5）
     */
    public float getArmorPoints(boolean charged) {
        return armorFactor(charged) * this.getAbsorption() * (float) this.getDamageAbsorption();
    }

    private static float armorFactor(boolean charged) {
        return charged ? 20.0F : 8.0F;
    }

    /**
     * 以下 getSet* 为同系列头盔+护腿+靴子+本胸甲的整套数值，只对胸甲有意义（进阶胸甲会拉高整套数值）
     */
    public float getSetArmorPoints(boolean charged) {
        return armorFactor(charged) * (0.15F + 0.3F + 0.15F) * (float) getSuiteDamageAbsorption() +
                getArmorPoints(charged);
    }

    public float getSetToughness(boolean charged) {
        float base = 4 * GTArmorMaterials.ARMOR.getToughness();
        return charged ? base + 3 * getSuiteExtraToughness() + getExtraToughness() : base;
    }

    public float getSetKnockbackResistance() {
        return 3 * getSuiteExtraKnockbackResistance() + getExtraKnockbackResistance();
    }

    /**
     * 同系列非胸甲部件的护甲倍率，进阶胸甲覆写 {@link #getDamageAbsorption()} 时用它算整套
     */
    public double getSuiteDamageAbsorption() {
        return getDamageAbsorption();
    }

    /**
     * 有电时在材质韧性之外额外附加的韧性
     */
    public float getExtraToughness() {
        return getSuiteExtraToughness();
    }

    public float getSuiteExtraToughness() {
        return 0;
    }

    /**
     * 有电时附加的击退抗性
     */
    public float getExtraKnockbackResistance() {
        return getSuiteExtraKnockbackResistance();
    }

    public float getSuiteExtraKnockbackResistance() {
        return 0;
    }

    @Override
    public void addToolComponents(ArmorComponentItem mvi) {
        mvi.attachComponents(new ElectricStats(maxCapacity, tier, true, false) {

            @Override
            public InteractionResultHolder<ItemStack> use(Item item, Level level, Player player,
                                                          InteractionHand usedHand) {
                return onRightClick(level, player, usedHand);
            }

            @Override
            public void appendTooltips(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
                                       TooltipFlag isAdvanced) {
                addInfo(stack, tooltipComponents);
            }
        });
    }

    /**
     * GTO: 电容量 = 1A × 电压 × 续航小时
     */
    public static long enduranceCapacity(int tier, int hours) {
        return GTValues.V[tier] * 20L * 3600L * hours;
    }

    /**
     * GTO: 满功率耗电为 1A，喷气背包飞行每 tick 扣这么多
     */
    public static int fullPowerDrain(int tier) {
        return (int) GTValues.V[tier];
    }

    public void addInfo(ItemStack itemStack, List<Component> lines) {
        IElectricItem cont = GTCapabilityHelper.getElectricItem(itemStack);
        if (cont != null) {
            ElectricStats.addCurrentChargeTooltip(lines, cont.getCharge(), cont.getMaxCharge(), cont.getTier(), false);
        }
        if (getDamageAbsorption() <= 0) return;
        // 电容量与防护数值只取决于本逻辑的常量，生成一次后复用
        if (staticInfo == null) {
            List<Component> info = new ArrayList<>(4);
            List<Component> details = new ArrayList<>(5);
            buildStaticInfo(info, details);
            staticDetails = details;
            staticInfo = info;
        }
        boolean showDetails = ArmorTooltips.showDetails();
        lines.addAll(staticInfo);
        if (showDetails) lines.addAll(staticDetails);
        List<Component> features = new ArrayList<>(24);
        addFeatures(itemStack, features);
        if (getGrade() > 0) ArmorSuiteFeatures.addCommonFeatures(this, itemStack, features);
        if (!features.isEmpty()) {
            lines.add(ArmorTooltips.section("features"));
            lines.addAll(features);
        }
        ArmorSuiteFeatures.addGaiaFeatures(this, itemStack, lines);
        if (!showDetails) lines.add(ArmorTooltips.SHIFT_HINT);
    }

    private void buildStaticInfo(List<Component> info, List<Component> details) {
        long voltage = GTValues.V[tier];
        double hours = maxCapacity / (voltage * 20D * 3600D);
        String hoursText = Math.abs(hours - Math.rint(hours)) < 0.01 ? String.valueOf(Math.round(hours)) :
                String.format("%.2f", hours);
        info.add(ArmorTooltips.info("capacity", ArmorTooltips.value(hoursText), GTValues.VNF[tier]));

        float baseToughness = GTArmorMaterials.ARMOR.getToughness();
        info.add(ArmorTooltips.section("protection"));
        info.add(ArmorTooltips.info("protection.piece", ArmorTooltips.value(formatValue(getArmorPoints(true))),
                ArmorTooltips.value(formatValue(baseToughness + getExtraToughness())),
                ArmorTooltips.value(formatPercent(getExtraKnockbackResistance()))));
        details.add(detail("detail.full_power"));
        details.add(detail("detail.unpowered_piece", formatValue(getArmorPoints(false)), formatValue(baseToughness)));
        if (type == ArmorItem.Type.CHESTPLATE) {
            float setArmor = getSetArmorPoints(true);
            float setToughness = getSetToughness(true);
            info.add(ArmorTooltips.info("protection.set", ArmorTooltips.value(formatValue(setArmor)),
                    ArmorTooltips.value(formatValue(setToughness)),
                    ArmorTooltips.value(formatPercent(getSetKnockbackResistance()))));
            details.add(detail("detail.unpowered_set", formatValue(getSetArmorPoints(false)),
                    formatValue(getSetToughness(false))));
            details.add(detail("detail.set_definition"));
            // Apothic Attributes 默认公式：单次伤害 < 20 时承受 10 / (10 + 护甲)；每点韧性抵抗 2% 破甲，60% 封顶
            details.add(detail("detail.set_effect", formatPercent(10.0F / (10.0F + setArmor)),
                    formatPercent(Math.min(setToughness * 0.02F, 0.6F))));
        }
    }

    private static Component detail(String key, Object... args) {
        return Component.literal("     ").append(ArmorTooltips.tr(key, args)).withStyle(ChatFormatting.DARK_GRAY);
    }

    /**
     * 功能列表，用 {@link ArmorTooltips#addFeature} 添加
     */
    protected void addFeatures(ItemStack itemStack, List<Component> features) {}

    private static String formatValue(float value) {
        return Math.abs(value - Math.round(value)) < 0.01F ? String.valueOf(Math.round(value)) :
                String.format("%.1f", value);
    }

    private static String formatPercent(float ratio) {
        return formatValue(ratio * 100) + "%";
    }

    public InteractionResultHolder<ItemStack> onRightClick(Level Level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public ArmorItem.Type getArmorType() {
        return type;
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return null;
    }

    public double getDamageAbsorption() {
        return 0;
    }

    @OnlyIn(Dist.CLIENT)
    protected static void addCapacityHUD(ItemStack stack, ArmorUtils.ModularHUD hud) {
        IElectricItem cont = GTCapabilityHelper.getElectricItem(stack);
        if (cont == null) return;
        if (cont.getCharge() == 0) return;
        float energyMultiplier = cont.getCharge() * 100.0F / cont.getMaxCharge();
        hud.newString(
                Component.translatable("metaarmor.hud.energy_lvl", String.format("%.1f", energyMultiplier) + "%"));
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public boolean shouldDrawHUD() {
        return this.type == ArmorItem.Type.CHESTPLATE;
    }

    protected float getAbsorption() {
        return switch (this.getArmorType()) {
            case HELMET, BOOTS -> 0.15F;
            case CHESTPLATE -> 0.4F;
            case LEGGINGS -> 0.3F;
        };
    }
}
