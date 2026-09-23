package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.api.item.armor.ArmorLogicSuite;
import com.gregtechceu.gtceu.api.item.component.IEnchantableItem;
import com.gregtechceu.gtceu.api.item.component.IItemAttributes;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.common.item.armor.ArmorTooltips;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * GTO: 纳米剑分为 I～IV 四级，电压与纳米肌体 / 夸克高科盔甲一致（MV / EV / LuV / UV）。
 * <p>
 * 潜行右键开关能量刃，开关总能切换；开启且电量足够时为激活伤害，并按目标最大生命值附加伤害，
 * 关闭或电量不足时为激活伤害的 30%。能量刃只在手持时按 1 A/s 耗电。
 */
public class NanoSaberBehavior extends ToggleEnergyConsumerBehavior implements IItemAttributes, IEnchantableItem {

    public static final ResourceLocation OVERRIDE_KEY_LOCATION = GTCEu.id("nano_saber_active");

    public static final int[] TIERS = { 0, GTValues.MV, GTValues.EV, GTValues.LuV, GTValues.UV };
    public static final int[] HOURS = { 0, 1, 3, 9, 27 };
    private static final double[] ACTIVE_DAMAGE = { 0, 20, 40, 60, 80 };
    private static final double[] ATTACK_SPEED = { 0, 2.0, 2.3, 2.6, 3.0 };
    private static final double[] ATTACK_RANGE = { 0, 3, 3, 4, 5 };
    /**
     * 附加伤害，单位为目标最大生命值的百分比
     */
    private static final double[] HEALTH_PERCENT = { 0, 0.1, 0.15, 0.2, 0.3 };

    public static final double ACTIVE_AMPS = 1.0;
    public static final double INACTIVE_DAMAGE_RATIO = 0.3;
    // 玩家自身的基础攻击伤害、攻击速度与实体交互距离，属性修饰只补差值
    private static final double PLAYER_ATTACK_DAMAGE = 1.0;
    private static final double PLAYER_ATTACK_SPEED = 4.0;
    private static final double PLAYER_ENTITY_REACH = 3.0;
    private static final UUID ATTACK_RANGE_UUID = UUID.fromString("5d3a6f0e-8c1b-4e52-9a47-2f6b0c9e1d84");

    private final int grade;
    private final int tier;

    public NanoSaberBehavior(int grade) {
        super((int) Math.round(ACTIVE_AMPS * GTValues.V[TIERS[grade]] / 20));
        this.grade = grade;
        this.tier = TIERS[grade];
    }

    public static long capacity(int grade) {
        return ArmorLogicSuite.enduranceCapacity(TIERS[grade], HOURS[grade]);
    }

    @Nullable
    public static NanoSaberBehavior get(ItemStack stack) {
        if (stack.getItem() instanceof ComponentItem item) {
            List<IItemComponent> components = item.getComponents();
            for (int i = 0; i < components.size(); i++) {
                if (components.get(i) instanceof NanoSaberBehavior saber) return saber;
            }
        }
        return null;
    }

    public double getActiveDamage() {
        return ACTIVE_DAMAGE[grade];
    }

    public double getInactiveDamage() {
        return ACTIVE_DAMAGE[grade] * INACTIVE_DAMAGE_RATIO;
    }

    public double getAttackDamage(boolean powered) {
        return powered ? getActiveDamage() : getInactiveDamage();
    }

    public double getAttackRange(boolean powered) {
        return powered ? ATTACK_RANGE[grade] : PLAYER_ENTITY_REACH;
    }

    public double getHealthPercent() {
        return HEALTH_PERCENT[grade];
    }

    /**
     * 能量刃已开启且电量足够
     */
    public boolean isPowered(ItemStack stack) {
        return isItemActive(stack) && ArmorTooltips.canUse(stack, energyUsagePerTick);
    }

    public static boolean isPoweredStack(ItemStack stack) {
        NanoSaberBehavior saber = get(stack);
        return saber != null && saber.isPowered(stack);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        HashMultimap<Attribute, AttributeModifier> modifiers = HashMultimap.create();
        if (slot == EquipmentSlot.MAINHAND) {
            double damage = getAttackDamage(isPowered(stack));
            modifiers.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_UUID,
                    "Weapon modifier", damage - PLAYER_ATTACK_DAMAGE, AttributeModifier.Operation.ADDITION));
            modifiers.put(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_UUID,
                    "Weapon modifier", ATTACK_SPEED[grade] - PLAYER_ATTACK_SPEED, AttributeModifier.Operation.ADDITION));
            // 攻击距离加成只在能量刃生效时提供
            double range = getAttackRange(isPowered(stack)) - PLAYER_ENTITY_REACH;
            if (range > 0) {
                modifiers.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(ATTACK_RANGE_UUID,
                        "Weapon modifier", range, AttributeModifier.Operation.ADDITION));
            }
        }
        return modifiers;
    }

    @Override
    public boolean hideAttributeTooltip(ItemStack stack) {
        return true;
    }

    /**
     * 手持时电量每 tick 变化，不因此反复播放重新装备动画
     */
    @Override
    public boolean suppressReequipOnNbtChange() {
        return true;
    }

    /**
     * 开关总能切换；电量不足时提示暂不生效
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Item item, Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) return InteractionResultHolder.pass(stack);
        boolean enabled = !isItemActive(stack);
        setItemActive(stack, enabled);
        if (!world.isClientSide) {
            player.displayClientMessage(
                    ArmorTooltips.toggleMessage(stack, "metaarmor.gto.name.energy_blade", enabled, isPowered(stack)), false);
        }
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide);
    }

    /**
     * 只在手持时耗电；电量不足时保持开启，充电后自动生效
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide || !isSelected || !isItemActive(stack)) return;
        IElectricItem electricItem = GTCapabilityHelper.getElectricItem(stack);
        if (electricItem != null && electricItem.canUse(energyUsagePerTick)) {
            electricItem.discharge(energyUsagePerTick, electricItem.getTier(), true, false, false);
        }
    }

    /**
     * 能量刃生效时，近战攻击在护甲结算之后按目标最大生命值附加伤害
     */
    public static void applyHealthPercentDamage(LivingDamageEvent event) {
        DamageSource source = event.getSource();
        if (event.getAmount() <= 0 || !(source.getEntity() instanceof LivingEntity attacker) ||
                source.getDirectEntity() != attacker) {
            return;
        }
        ItemStack weapon = attacker.getMainHandItem();
        NanoSaberBehavior saber = get(weapon);
        if (saber == null || !saber.isPowered(weapon)) return;
        LivingEntity target = event.getEntity();
        event.setAmount(event.getAmount() + (float) (target.getMaxHealth() * saber.getHealthPercent() / 100));
    }

    /**
     * 武器性能按当前状态取值：能量刃生效为绿；已开启但电量不足为黄、已关闭为红，并在行尾注明原因。
     * 攻击速度不受能量刃影响。
     */
    @Override
    public void appendTooltips(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag isAdvanced) {
        boolean enabled = isItemActive(stack);
        boolean hasEnergy = ArmorTooltips.canUse(stack, energyUsagePerTick);
        boolean powered = enabled && hasEnergy;
        ChatFormatting color = powered ? ChatFormatting.GREEN :
                enabled ? ChatFormatting.YELLOW : ChatFormatting.RED;
        Component reason = powered ? null : ArmorTooltips.reason(
                Component.translatable(enabled ? "metaarmor.gto.state.no_energy" : "metaarmor.gto.state.blade_off"), color);

        lines.add(ArmorTooltips.info("metaarmor.gto.capacity", ArmorTooltips.value(Integer.toString(HOURS[grade])),
                GTValues.VNF[tier]));
        lines.add(ArmorTooltips.section("metaarmor.gto.section.weapon"));
        lines.add(stateLine("metaarmor.gto.saber.damage", ArmorTooltips.amps(getAttackDamage(powered)), color, reason));
        lines.add(stateLine("metaarmor.gto.saber.health_percent", ArmorTooltips.amps(powered ? getHealthPercent() : 0) + "%",
                color, reason));
        // I、II 没有攻击距离加成，按常量显示
        lines.add(ATTACK_RANGE[grade] > PLAYER_ENTITY_REACH ?
                stateLine("metaarmor.gto.saber.range", ArmorTooltips.amps(getAttackRange(powered)), color, reason) :
                ArmorTooltips.info("metaarmor.gto.saber.range", ArmorTooltips.value(ArmorTooltips.amps(PLAYER_ENTITY_REACH))));
        lines.add(ArmorTooltips.info("metaarmor.gto.saber.speed", ArmorTooltips.value(ArmorTooltips.amps(ATTACK_SPEED[grade]))));
        lines.add(ArmorTooltips.section("metaarmor.gto.section.features"));
        ArmorTooltips.addFeature(lines, "metaarmor.gto.feature.energy_blade", ArmorTooltips.shiftUseToggle(enabled, hasEnergy),
                ArmorTooltips.ampsPerSecond(ACTIVE_AMPS));
        ArmorTooltips.addDetail(lines, "metaarmor.gto.detail.energy_blade", ArmorTooltips.amps(getActiveDamage()),
                ArmorTooltips.amps(getHealthPercent()) + "%", ArmorTooltips.amps(ATTACK_RANGE[grade]),
                ArmorTooltips.amps(getInactiveDamage()), ArmorTooltips.amps(PLAYER_ENTITY_REACH));
        ArmorTooltips.addDetail(lines, "metaarmor.gto.detail.energy_blade_drain");
        if (!ArmorTooltips.showDetails()) lines.add(ArmorTooltips.SHIFT_HINT);
    }

    private static Component stateLine(String key, String value, ChatFormatting color, @Nullable Component reason) {
        MutableComponent line = ArmorTooltips.info(key, ArmorTooltips.value(value, color));
        if (reason != null) line.append(reason);
        return line;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 33;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if (enchantment.category == null) {
            return false;
        }
        return enchantment != Enchantments.UNBREAKING &&
                enchantment != Enchantments.MENDING &&
                enchantment.category.canEnchant(Items.IRON_SWORD);
    }
}
