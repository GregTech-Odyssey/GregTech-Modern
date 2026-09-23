package com.gregtechceu.gtceu.common.item.armor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;
import com.gregtechceu.gtceu.api.item.armor.ArmorLogicSuite;
import com.gregtechceu.gtceu.api.item.armor.ArmorUtils;
import com.gregtechceu.gtceu.common.data.GTItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NanoMuscleSuite extends ArmorLogicSuite implements IStepAssist {

    @OnlyIn(Dist.CLIENT)
    protected ArmorUtils.ModularHUD HUD;

    public NanoMuscleSuite(ArmorItem.Type slot, int energyPerUse, long maxCapacity, int tier) {
        super(energyPerUse, maxCapacity, tier, slot);
        if (GTCEu.isClientSide() && this.shouldDrawHUD()) {
            // noinspection NewExpressionSideOnly
            HUD = new ArmorUtils.ModularHUD();
        }
    }

    @Override
    public void onArmorTick(Level world, Player player, ItemStack itemStack) {
        ArmorSuiteFeatures.tick(this, world, player, itemStack);
        if (type == ArmorItem.Type.LEGGINGS) tickSpeedLeggings(world, player, itemStack);
    }

    @Override
    public int getGrade() {
        return 1;
    }

    /**
     * GTO: 受击、摔落每点伤害消耗 1/3600 A·h
     */
    public static final int DAMAGE_COST_PARTS = 1;

    public boolean handleUnblockableDamage(LivingEntity entity, @NotNull ItemStack armor, DamageSource source,
                                           double damage, ArmorItem.Type equipmentSlot) {
        return source.is(DamageTypes.FALL);
    }

    /*
     * @Override
     * public ArmorProperties getProperties(EntityLivingBase player, @NotNull ItemStack armor, DamageSource source,
     * double damage, ArmorItem.Type equipmentSlot) {
     * IElectricItem container = armor.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
     * int damageLimit = Integer.MAX_VALUE;
     * if (source == DamageSource.FALL && this.getEquipmentSlot(armor) == ArmorItem.Type.FEET) {
     * if (energyPerUse > 0 && container != null) {
     * damageLimit = (int) Math.min(damageLimit, 25.0 * container.getCharge() / (energyPerUse * 10.0D));
     * }
     * return new ArmorProperties(10, (damage < 8.0) ? 1.0 : 0.875, damageLimit);
     * }
     * return super.getProperties(player, armor, source, damage, equipmentSlot);
     * }
     */

    @Override
    public int damageArmor(LivingEntity entity, ItemStack itemStack, DamageSource source, int damage,
                           EquipmentSlot equipmentSlot) {
        IElectricItem item = GTCapabilityHelper.getElectricItem(itemStack);
        if (item != null) {
            item.discharge(ampHourParts(DAMAGE_COST_PARTS) * damage, item.getTier(), true, false, false);
        }
        return super.damageArmor(entity, itemStack, source, damage, equipmentSlot);
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        ItemStack currentChest = Minecraft.getInstance().player.getInventory()
                .getArmor(ArmorItem.Type.CHESTPLATE.getSlot().getIndex());
        ItemStack advancedChest = GTItems.NANO_CHESTPLATE_ADVANCED.asStack();
        String armorTexture = "nano_muscule_suite";
        if (advancedChest.is(currentChest.getItem())) armorTexture = "advanced_nano_muscle_suite";
        return slot != EquipmentSlot.LEGS ?
                GTCEu.id(String.format("textures/armor/%s_1.png", armorTexture)) :
                GTCEu.id(String.format("textures/armor/%s_2.png", armorTexture));
    }

    @Override
    public double getDamageAbsorption() {
        return getSuiteDamageAbsorption();
    }

    // GTO: 1.0 -> 1.5，整套 20 -> 30 护甲
    @Override
    public double getSuiteDamageAbsorption() {
        return 1.5D;
    }

    // GTO: 护腿疾跑倍率 I 只有 1.8 倍一档
    @Override
    public int getMaxSpeedLevel() {
        return 1;
    }

    @Override
    public float getHeatResistance() {
        return 0.75f;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void drawHUD(ItemStack item, GuiGraphics guiGraphics) {
        addCapacityHUD(item, this.HUD);
        this.HUD.draw(guiGraphics);
        this.HUD.reset();
    }

    @Override
    protected void addFeatures(ItemStack itemStack, List<Component> features) {
        ArmorTooltips.addFeature(features, "metaarmor.gto.feature.damage_drain", ArmorTooltips.piecePassive(),
                ArmorTooltips.ampHours("metaarmor.gto.cost.per_damage", DAMAGE_COST_PARTS));
        if (type == ArmorItem.Type.HELMET) {
            ArmorTooltips.addDetail(features, "metaarmor.gto.detail.no_nightvision");
        } else if (type == ArmorItem.Type.LEGGINGS) {
            ArmorTooltips.addSpeedFeature(itemStack, this, features);
        } else if (type == ArmorItem.Type.BOOTS) {
            ArmorTooltips.addFeature(features, "metaarmor.gto.feature.step_assist", ArmorTooltips.piecePassive(), null);
            ArmorTooltips.addDetail(features, "metaarmor.gto.detail.step_assist");
        }
    }
}
