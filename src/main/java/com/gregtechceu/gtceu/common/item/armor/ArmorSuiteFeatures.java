package com.gregtechceu.gtceu.common.item.armor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;
import com.gregtechceu.gtceu.api.item.armor.ArmorComponentItem;
import com.gregtechceu.gtceu.api.item.armor.ArmorLogicSuite;
import com.gregtechceu.gtceu.utils.input.KeyBind;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * GTO: 纳米肌体 / 夸克高科四套（I~IV）共用的功能：生命强化、环境伤害抵扣、定时清除负面效果、
 * 生命恢复（III 起）、机械辅助，以及 II 起对 ExtraBotany 盖亚守护者 III 的法阵解析、抗缴械、缴械地雷解析。
 * <p>
 * 持续耗电以 A/s 标示（实际每 tick 扣 1/20 或每秒扣一次），一次性耗电为 n/3600 A·h，均按该件装备自身电压计。
 */
@Mod.EventBusSubscriber(modid = GTCEu.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ArmorSuiteFeatures {

    public static final String LIFE_BOOST = "lifeBoost";
    public static final double LIFE_AMPS = 0.1;
    public static final int CLEANSE_PARTS = 20;
    public static final int CLEANSE_INTERVAL = 40;
    public static final int REGEN_PARTS = 10;
    /**
     * 机械辅助每点额外伤害的耗电，单位 1/3600 A·h
     */
    public static final int MECH_ASSIST_PARTS_PER_DAMAGE = 5;
    public static final int GAIA_INVENTORY_PARTS = 50;
    public static final double GAIA_DISARM_AMPS = 1.0;
    public static final int GAIA_MINE_PARTS = 100;

    /**
     * 按伤害抵扣的装备遍历顺序
     */
    private static final EquipmentSlot[] ABSORB_ORDER = { EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.HEAD, EquipmentSlot.FEET };

    private ArmorSuiteFeatures() {}

    // 装备查询

    @Nullable
    public static ArmorLogicSuite getSuite(ItemStack stack) {
        if (stack.getItem() instanceof ArmorComponentItem armor &&
                armor.getArmorLogic() instanceof ArmorLogicSuite suite && suite.getGrade() > 0) {
            return suite;
        }
        return null;
    }

    public static boolean isLifeBoostEnabled(ItemStack stack) {
        CompoundTag data = stack.getTag();
        return data == null || !data.contains(LIFE_BOOST) || data.getBoolean(LIFE_BOOST);
    }

    public static long lifeBoostCostPerSecond(ArmorLogicSuite suite) {
        return Math.round(LIFE_AMPS * GTValues.V[suite.getTier()]);
    }

    private static boolean pay(ItemStack stack, long cost) {
        IElectricItem item = GTCapabilityHelper.getElectricItem(stack);
        if (item == null || !item.canUse(cost)) return false;
        item.discharge(cost, item.getTier(), true, false, false);
        return true;
    }

    // 每 tick 逻辑，由各套装的 onArmorTick 调用

    public static void tick(ArmorLogicSuite suite, Level world, Player player, ItemStack stack) {
        if (world.isClientSide) return;
        IElectricItem item = GTCapabilityHelper.getElectricItem(stack);
        if (item == null) return;

        if (KeyBind.ARMOR_LIFE.consumePress(player)) toggleLifeBoost(player, stack);
        if (player.tickCount % 20 == 0 && isLifeBoostEnabled(stack)) {
            pay(stack, lifeBoostCostPerSecond(suite));
        }

        ArmorItem.Type type = suite.getArmorType();
        if (type == ArmorItem.Type.HELMET && player.tickCount % CLEANSE_INTERVAL == 0) {
            cleanseHarmfulEffects(suite, player, stack);
        }
        // 每 4/3 秒（80/3 tick）回 1 点生命
        if (type == ArmorItem.Type.CHESTPLATE && suite.getGrade() >= 3 && player.isHurt()) {
            int phase = player.tickCount % 80;
            if ((phase == 0 || phase == 27 || phase == 53) && pay(stack, suite.ampHourParts(REGEN_PARTS))) {
                player.heal(1.0F);
            }
        }
    }

    /**
     * 按键切换身上所有四套装备的生命强化，以触发按键的这一件的当前状态为准
     */
    private static void toggleLifeBoost(Player player, ItemStack trigger) {
        boolean enabled = !isLifeBoostEnabled(trigger);
        boolean effective = true;
        for (ItemStack piece : player.getInventory().armor) {
            ArmorLogicSuite suite = getSuite(piece);
            if (suite == null) continue;
            piece.getOrCreateTag().putBoolean(LIFE_BOOST, enabled);
            IElectricItem item = GTCapabilityHelper.getElectricItem(piece);
            if (item == null || !item.canUse(lifeBoostCostPerSecond(suite))) effective = false;
        }
        player.displayClientMessage(ArmorTooltips.toggleMessage(trigger, "metaarmor.gto.name.life_boost", enabled, effective), false);
        // 最大生命值降低时，当前生命值不超过新上限
        if (!enabled && player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static void cleanseHarmfulEffects(ArmorLogicSuite suite, Player player, ItemStack stack) {
        if (player.getActiveEffects().isEmpty()) return;
        for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
            if (effect.getEffect().getCategory() != MobEffectCategory.HARMFUL) continue;
            if (pay(stack, suite.ampHourParts((double) CLEANSE_PARTS * (effect.getAmplifier() + 1)))) {
                player.removeEffect(effect.getEffect());
            }
        }
    }

    // 伤害事件

    public static boolean isInstantEnvironmentDamage(DamageSource source) {
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_LIGHTNING) ||
                source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.FALLING_ANVIL);
    }

    public static boolean isContinuousEnvironmentDamage(DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_DROWNING) ||
                source.is(DamageTypeTags.IS_FREEZING) || source.is(DamageTypes.IN_WALL) ||
                source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.STARVE) || source.is(DamageTypes.CACTUS);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        // 环境伤害抵扣：身上任意一件有电即按伤害抵扣
        if (event.getEntity() instanceof Player player &&
                (isInstantEnvironmentDamage(source) || isContinuousEnvironmentDamage(source))) {
            for (EquipmentSlot slot : ABSORB_ORDER) {
                ItemStack piece = player.getItemBySlot(slot);
                ArmorLogicSuite suite = getSuite(piece);
                if (suite != null &&
                        pay(piece, suite.ampHourParts((double) NanoMuscleSuite.DAMAGE_COST_PARTS * event.getAmount()))) {
                    event.setAmount(0);
                    event.setCanceled(true);
                    return;
                }
            }
        }
        // 机械辅助（II 起）：主手空置时近战攻击额外 10 / 15 / 20 点伤害
        // 与 ExtraBotany 女仆套装的空手怪力同一时机，对盖亚守护者同样生效
        if (source.getEntity() instanceof Player attacker && source.getDirectEntity() == attacker &&
                event.getEntity() != attacker && attacker.getMainHandItem().isEmpty()) {
            ItemStack chest = attacker.getItemBySlot(EquipmentSlot.CHEST);
            ArmorLogicSuite suite = getSuite(chest);
            if (suite != null && suite.getArmorType() == ArmorItem.Type.CHESTPLATE) {
                float bonus = getMechAssistDamage(suite);
                if (bonus > 0 && pay(chest, getMechAssistCost(suite))) {
                    event.setAmount(event.getAmount() + bonus);
                }
            }
        }
    }

    /**
     * 机械辅助额外伤害：II 10、III 15、IV 20，I 没有
     */
    public static float getMechAssistDamage(ArmorLogicSuite suite) {
        return suite.getGrade() >= 2 ? 5.0F * suite.getGrade() : 0;
    }

    public static long getMechAssistCost(ArmorLogicSuite suite) {
        return suite.ampHourParts((double) MECH_ASSIST_PARTS_PER_DAMAGE * getMechAssistDamage(suite));
    }

    // ExtraBotany 盖亚守护者 III（由 GTOCore 的 mixin 调用）

    /**
     * II 起的四套装备可以对抗盖亚守护者 III
     */
    public static boolean isGaiaResistant(ItemStack stack) {
        ArmorLogicSuite suite = getSuite(stack);
        return suite != null && suite.getGrade() >= 2;
    }

    public static boolean canPayGaiaInventory(ItemStack stack) {
        ArmorLogicSuite suite = getSuite(stack);
        IElectricItem item = GTCapabilityHelper.getElectricItem(stack);
        return suite != null && item != null && item.canUse(suite.ampHourParts(GAIA_INVENTORY_PARTS));
    }

    public static boolean payGaiaInventory(ItemStack stack) {
        ArmorLogicSuite suite = getSuite(stack);
        return suite != null && pay(stack, suite.ampHourParts(GAIA_INVENTORY_PARTS));
    }

    /**
     * 抗缴械：每 tick 扣 1 A/s 的 1/20
     */
    public static boolean payGaiaDisarm(ItemStack stack) {
        ArmorLogicSuite suite = getSuite(stack);
        return suite != null && pay(stack, suite.ampsPerSecond(GAIA_DISARM_AMPS));
    }

    /**
     * 缴械地雷解析：由身上任意一件 II 起的装备支付
     */
    public static boolean payGaiaMine(Player player) {
        for (EquipmentSlot slot : ABSORB_ORDER) {
            ItemStack piece = player.getItemBySlot(slot);
            if (!isGaiaResistant(piece)) continue;
            ArmorLogicSuite suite = getSuite(piece);
            if (suite != null && pay(piece, suite.ampHourParts(GAIA_MINE_PARTS))) return true;
        }
        return false;
    }

    public static Component gaiaNoEnergyMessage(ItemStack stack) {
        return Component.empty().append(stack.getDisplayName()).append(" ")
                .append(Component.translatable("metaarmor.gto.gaia.no_energy").withStyle(ChatFormatting.RED));
    }

    // tooltip

    /**
     * 四套共有的功能行，追加在各套装自身功能之后
     */
    public static void addCommonFeatures(ArmorLogicSuite suite, ItemStack stack, List<Component> features) {
        CompoundTag data = stack.getTag();
        long lifeCost = lifeBoostCostPerSecond(suite);
        ArmorTooltips.addFeature(features, "metaarmor.gto.feature.life_boost",
                ArmorTooltips.keyToggle(KeyBind.ARMOR_LIFE, isLifeBoostEnabled(stack), ArmorTooltips.canUse(stack, lifeCost)),
                ArmorTooltips.ampsPerSecond(LIFE_AMPS));
        ArmorTooltips.addDetail(features, "metaarmor.gto.detail.life_boost", ArmorTooltips.amps(suite.getPieceHealthBoost()),
                suite.getGrade() * 10);
        boolean canAbsorb = ArmorTooltips.canUse(stack, suite.ampHourParts(NanoMuscleSuite.DAMAGE_COST_PARTS));
        ArmorTooltips.addFeature(features, "metaarmor.gto.feature.env_instant", ArmorTooltips.piecePassive(canAbsorb),
                ArmorTooltips.ampHours("metaarmor.gto.cost.per_damage", NanoMuscleSuite.DAMAGE_COST_PARTS));
        ArmorTooltips.addDetail(features, "metaarmor.gto.detail.env_instant");
        ArmorTooltips.addFeature(features, "metaarmor.gto.feature.env_continuous", ArmorTooltips.piecePassive(canAbsorb),
                ArmorTooltips.ampHours("metaarmor.gto.cost.per_damage", NanoMuscleSuite.DAMAGE_COST_PARTS));
        ArmorTooltips.addDetail(features, "metaarmor.gto.detail.env_continuous");
        ArmorItem.Type type = suite.getArmorType();
        if (type == ArmorItem.Type.HELMET) {
            ArmorTooltips.addFeature(features, "metaarmor.gto.feature.timed_cleanse",
                    ArmorTooltips.piecePassive(ArmorTooltips.canUse(stack, suite.ampHourParts(CLEANSE_PARTS))),
                    ArmorTooltips.ampHours("metaarmor.gto.cost.per_level", CLEANSE_PARTS));
            ArmorTooltips.addDetail(features, "metaarmor.gto.detail.timed_cleanse");
        }
        if (type == ArmorItem.Type.CHESTPLATE) {
            if (suite.getGrade() >= 3) {
                ArmorTooltips.addFeature(features, "metaarmor.gto.feature.regen",
                        ArmorTooltips.piecePassive(ArmorTooltips.canUse(stack, suite.ampHourParts(REGEN_PARTS))),
                        ArmorTooltips.ampHours("metaarmor.gto.cost.per_heal", REGEN_PARTS));
                ArmorTooltips.addDetail(features, "metaarmor.gto.detail.regen");
            }
            if (suite.getGrade() >= 2) {
                ArmorTooltips.addFeature(features, "metaarmor.gto.feature.mech_assist",
                        ArmorTooltips.piecePassive(ArmorTooltips.canUse(stack, getMechAssistCost(suite))),
                        ArmorTooltips.ampHours("metaarmor.gto.cost.per_damage", MECH_ASSIST_PARTS_PER_DAMAGE));
                ArmorTooltips.addDetail(features, "metaarmor.gto.detail.mech_assist", ArmorTooltips.amps(getMechAssistDamage(suite)));
            }
        }
    }

    /**
     * II 起：盖亚守护者 III 对抗
     */
    public static void addGaiaFeatures(ArmorLogicSuite suite, ItemStack stack, List<Component> lines) {
        if (suite.getGrade() < 2) return;
        lines.add(ArmorTooltips.section("metaarmor.gto.section.gaia"));
        ArmorTooltips.addFeature(lines, "metaarmor.gto.feature.gaia_inventory",
                ArmorTooltips.piecePassive(ArmorTooltips.canUse(stack, suite.ampHourParts(GAIA_INVENTORY_PARTS))),
                ArmorTooltips.ampHours("metaarmor.gto.cost.per_summon", GAIA_INVENTORY_PARTS));
        ArmorTooltips.addDetail(lines, "metaarmor.gto.detail.gaia_inventory");
        ArmorTooltips.addFeature(lines, "metaarmor.gto.feature.gaia_disarm",
                ArmorTooltips.piecePassive(ArmorTooltips.canUse(stack, suite.ampsPerSecond(GAIA_DISARM_AMPS))),
                ArmorTooltips.ampsPerSecond(GAIA_DISARM_AMPS));
        ArmorTooltips.addDetail(lines, "metaarmor.gto.detail.gaia_disarm");
        if (suite.getArmorType() == ArmorItem.Type.CHESTPLATE) {
            ArmorTooltips.addFeature(lines, "metaarmor.gto.feature.gaia_mine",
                    ArmorTooltips.piecePassive(ArmorTooltips.canUse(stack, suite.ampHourParts(GAIA_MINE_PARTS))),
                    ArmorTooltips.ampHours("metaarmor.gto.cost.per_use", GAIA_MINE_PARTS));
            ArmorTooltips.addDetail(lines, "metaarmor.gto.detail.gaia_mine");
        }
    }
}
