package com.gregtechceu.gtceu.common.item.armor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 纳米肌体进阶套装 (II)。GTO: 补齐为完整四件，胸甲带喷气背包，其余部件与纳米肌体 (I) 行为一致、数值更高
 */
public class AdvancedNanoMuscleSuite extends NanoMuscleSuite implements IJetpack {

    public AdvancedNanoMuscleSuite(ArmorItem.Type slot, int energyPerUse, long capacity, int tier) {
        super(slot, energyPerUse, capacity, tier);
    }

    public AdvancedNanoMuscleSuite(int energyPerUse, long capacity, int tier) {
        this(ArmorItem.Type.CHESTPLATE, energyPerUse, capacity, tier);
    }

    @Override
    public void onArmorTick(Level world, Player player, @NotNull ItemStack item) {
        if (type == ArmorItem.Type.CHESTPLATE) {
            ArmorSuiteFeatures.tick(this, world, player, item);
            JetpackChestHelper.tick(this, world, player, item);
        } else {
            super.onArmorTick(world, player, item);
        }
    }

    @Override
    protected void addFeatures(ItemStack itemStack, List<Component> features) {
        super.addFeatures(itemStack, features);
        ArmorTooltips.addFeature(features, "ppe", ArmorTooltips.setPassive(itemStack, ArmorTooltips::isPPE), null);
        ArmorTooltips.addDetail(features, "detail.ppe");
        // GTO: 放射性材料危害要求四个部位均为防护装备，II 及以上每件都算
        ArmorTooltips.addFeature(features, "radiation", ArmorTooltips.setPassive(itemStack, ArmorTooltips::isPPE),
                null);
        ArmorTooltips.addDetail(features, "detail.radiation");
        if (type == ArmorItem.Type.CHESTPLATE) ArmorTooltips.addJetpackFeatures(itemStack, this, features);
    }

    @Override
    public InteractionResultHolder<ItemStack> onRightClick(Level world, @NotNull Player player, InteractionHand hand) {
        if (type == ArmorItem.Type.CHESTPLATE && player.isShiftKeyDown()) {
            return JetpackChestHelper.onShiftUse(world, player, hand);
        }
        return super.onRightClick(world, player, hand);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void drawHUD(ItemStack item, GuiGraphics guiGraphics) {
        addCapacityHUD(item, this.HUD);
        if (canUseEnergy(item, energyPerUse)) JetpackChestHelper.addHudLines(item, this.HUD);
        this.HUD.draw(guiGraphics);
        this.HUD.reset();
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        if (this.type == ArmorItem.Type.CHESTPLATE) return GTCEu.id("textures/armor/advanced_nano_muscle_suite_1.png");
        return super.getArmorTexture(stack, entity, slot, type);
    }

    @Override
    public int getGrade() {
        return 2;
    }

    // GTO: 整套护甲 45、韧性 24、击退抗性 1/6

    @Override
    public double getSuiteDamageAbsorption() {
        return 2.25D;
    }

    @Override
    public float getSuiteExtraToughness() {
        return 1.0F;
    }

    @Override
    public float getSuiteExtraKnockbackResistance() {
        return 1.0F / 24;
    }

    @Override
    public int getMaxSpeedLevel() {
        return 2;
    }

    @Override
    public boolean isPPE() {
        return true;
    }

    // 喷气背包

    @Override
    public int getFlightEnergyPerTick() {
        return (int) ampsPerSecond(JetpackChestHelper.FLIGHT_AMPS);
    }

    @Override
    public boolean canUseEnergy(@NotNull ItemStack stack, int amount) {
        IElectricItem container = GTCapabilityHelper.getElectricItem(stack);
        return container != null && container.canUse(amount);
    }

    @Override
    public void drainEnergy(@NotNull ItemStack stack, int amount) {
        IElectricItem container = GTCapabilityHelper.getElectricItem(stack);
        if (container != null) container.discharge(amount, tier, true, false, false);
    }

    @Override
    public boolean hasEnergy(@NotNull ItemStack stack) {
        IElectricItem container = GTCapabilityHelper.getElectricItem(stack);
        return container != null && container.getCharge() > 0;
    }

    @Override
    public double getSprintSpeedModifier() {
        return 1.8D;
    }

    @Override
    public double getVerticalHoverSpeed() {
        return 0.4D;
    }

    @Override
    public double getVerticalHoverSlowSpeed() {
        return 0.005D;
    }

    @Override
    public double getVerticalAcceleration() {
        return 0.14D;
    }

    @Override
    public double getVerticalSpeed() {
        return 0.8D;
    }

    @Override
    public double getSidewaysSpeed() {
        return 0.19D;
    }

    @Nullable
    @Override
    public ParticleOptions getParticle() {
        return null;
    }

    @Override
    public float getFallDamageReduction() {
        return 3.5f;
    }
}
