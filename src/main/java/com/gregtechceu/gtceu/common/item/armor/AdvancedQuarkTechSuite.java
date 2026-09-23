package com.gregtechceu.gtceu.common.item.armor;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * 夸克高科进阶套装 (IV)。GTO: 补齐为完整四件，行为沿用 {@link QuarkTechSuite}（胸甲带喷气背包），这里只覆盖数值与贴图
 */
public class AdvancedQuarkTechSuite extends QuarkTechSuite {

    public AdvancedQuarkTechSuite(ArmorItem.Type slot, int energyPerUse, long capacity, int tier) {
        super(slot, energyPerUse, capacity, tier);
    }

    public AdvancedQuarkTechSuite(int energyPerUse, long capacity, int tier) {
        this(ArmorItem.Type.CHESTPLATE, energyPerUse, capacity, tier);
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        if (this.type == ArmorItem.Type.CHESTPLATE) return GTCEu.id("textures/armor/advanced_quark_tech_suite_1.png");
        return super.getArmorTexture(stack, entity, slot, type);
    }

    @Override
    public int getGrade() {
        return 4;
    }

    // GTO: 整套护甲 90、韧性 30、击退抗性 50%

    @Override
    public double getSuiteDamageAbsorption() {
        return 4.5D;
    }

    @Override
    public float getSuiteExtraToughness() {
        return 2.5F;
    }

    @Override
    public float getSuiteExtraKnockbackResistance() {
        return 0.125F;
    }

    // GTO: 护腿疾跑倍率 IV 可切换到 6 倍
    @Override
    public int getMaxSpeedLevel() {
        return 4;
    }

    @Override
    public double getSprintSpeedModifier() {
        return 2.4D;
    }

    @Override
    public double getVerticalHoverSpeed() {
        return 0.45D;
    }

    @Override
    public double getVerticalHoverSlowSpeed() {
        return 0.0D;
    }

    @Override
    public double getVerticalAcceleration() {
        return 0.15D;
    }

    @Override
    public double getVerticalSpeed() {
        return 0.9D;
    }

    @Override
    public double getSidewaysSpeed() {
        return 0.21D;
    }

    @Override
    public float getFallDamageReduction() {
        return 8f;
    }
}
