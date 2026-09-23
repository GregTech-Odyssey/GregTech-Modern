package com.gregtechceu.gtceu.common.item.armor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;
import com.gregtechceu.gtceu.api.item.armor.ArmorComponentItem;
import com.gregtechceu.gtceu.api.item.armor.ArmorLogicSuite;
import com.gregtechceu.gtceu.api.item.armor.ArmorUtils;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.core.IFireImmuneEntity;
import com.gregtechceu.gtceu.utils.input.KeyBind;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class QuarkTechSuite extends ArmorLogicSuite implements IStepAssist, IJetpack {

    /**
     * GTO: 清除负面效果每级耗电，单位 1/3600 A·h（按头盔自身电压计）
     */
    public static final Reference2IntMap<MobEffect> potionRemovalCost = new Reference2IntOpenHashMap<>();
    /**
     * GTO: 一次性耗电，单位 1/3600 A·h
     */
    public static final int BREATH_COST_PARTS = 1;
    public static final int FOOD_COST_PARTS = 5;
    public static final int JUMP_COST_PARTS = 1;
    private float charge = 0.0F;

    @OnlyIn(Dist.CLIENT)
    protected ArmorUtils.ModularHUD HUD;

    public QuarkTechSuite(ArmorItem.Type slot, int energyPerUse, long capacity, int tier) {
        super(energyPerUse, capacity, tier, slot);
        potionRemovalCost.put(MobEffects.POISON, 10);
        potionRemovalCost.put(MobEffects.WITHER, 25);
        potionRemovalCost.put(MobEffects.CONFUSION, 8);
        potionRemovalCost.put(MobEffects.DIG_SLOWDOWN, 12);
        potionRemovalCost.put(MobEffects.MOVEMENT_SLOWDOWN, 9);
        potionRemovalCost.put(MobEffects.UNLUCK, 5);
        if (GTCEu.isClientSide() && this.shouldDrawHUD()) {
            HUD = new ArmorUtils.ModularHUD();
        }
    }

    @Override
    public void onArmorTick(Level world, Player player, ItemStack itemStack) {
        ArmorSuiteFeatures.tick(this, world, player, itemStack);
        IElectricItem item = GTCapabilityHelper.getElectricItem(itemStack);
        if (item == null)
            return;

        CompoundTag data = itemStack.getOrCreateTag();

        // GTO: 按逻辑判断夸克胸甲，太空版夸克胸甲也算，否则穿太空胸甲时免疫燃烧会每 tick 被关掉
        if (!world.isClientSide && !isQuarkChestplate(player.getItemBySlot(EquipmentSlot.CHEST))) {
            ((IFireImmuneEntity) player).gtceu$setFireImmune(false);
        }

        boolean ret = false;
        if (type == ArmorItem.Type.HELMET) {
            ret = supplyAir(item, player) || supplyFood(item, player);

            removeNegativeEffects(item, player);
        } else if (type == ArmorItem.Type.CHESTPLATE) {
            if (!player.fireImmune()) {
                ((IFireImmuneEntity) player).gtceu$setFireImmune(true);
                if (player.isOnFire()) player.extinguishFire();
            }
            // GTO: 夸克高科胸甲 (III) 起自带喷气背包
            JetpackChestHelper.tick(this, world, player, itemStack);
        } else if (type == ArmorItem.Type.LEGGINGS) {
            // GTO: 原疾跑加速改为护腿通用的疾跑倍率
            tickSpeedLeggings(world, player, itemStack);
        } else if (type == ArmorItem.Type.BOOTS) {
            long jumpCost = ampHourParts(JUMP_COST_PARTS);
            boolean canUseEnergy = item.canUse(jumpCost);
            boolean jumping = KeyBind.VANILLA_JUMP.isKeyDown(player);
            boolean boostedJump = data.getBoolean("boostedJump");
            // GTO: 只在服务端按下沿切换一次，再随物品同步到客户端
            if (!world.isClientSide && KeyBind.BOOTS_ENABLE.consumePress(player)) {
                boostedJump = !boostedJump;
                data.putBoolean("boostedJump", boostedJump);
                player.displayClientMessage(
                        ArmorTooltips.toggleMessage(itemStack, "boosted_jump", boostedJump, canUseEnergy), false);
            }
            if (boostedJump) {
                if (!world.isClientSide) {
                    boolean onGround = !data.contains("onGround") || data.getBoolean("onGround");
                    if (onGround && !player.onGround() && jumping) {
                        item.discharge(jumpCost, item.getTier(), true, false, false);
                        ret = true;
                    }

                    if (player.onGround() != onGround) {
                        data.putBoolean("onGround", player.onGround());
                    }
                } else {
                    if (canUseEnergy && player.onGround()) {
                        this.charge = 1.0F;
                    }

                    Vec3 delta = player.getDeltaMovement();
                    if (delta.y >= 0.0D && this.charge > 0.0F && !player.isInWater()) {
                        if (jumping) {
                            if (this.charge == 1.0F) {
                                player.setDeltaMovement(delta.x * 3.6D, delta.y, delta.z * 3.6D);
                            }
                            // gives an arc path for movement force
                            player.addDeltaMovement(new Vec3(0.0, this.charge * 0.32, 0.0));
                            this.charge = (float) (this.charge * 0.7D);
                        } else if (this.charge < 1.0F) {
                            this.charge = 0.0F;
                        }
                    }
                }
            }
        }

        if (ret) {
            player.inventoryMenu.sendAllDataToRemote();
        }
    }

    public boolean supplyAir(@NotNull IElectricItem item, Player player) {
        int air = player.getAirSupply();
        long cost = ampHourParts(BREATH_COST_PARTS);
        if (item.canUse(cost) && air < 100) {
            player.setAirSupply(air + 200);
            item.discharge(cost, item.getTier(), true, false, false);
            return true;
        }
        return false;
    }

    public boolean supplyFood(@NotNull IElectricItem item, Player player) {
        long cost = ampHourParts(FOOD_COST_PARTS);
        if (item.canUse(cost) && player.getFoodData().needsFood()) {
            int slotId = -1;
            IItemHandler playerInv = player.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (playerInv instanceof IItemHandlerModifiable items) {
                for (int i = 0; i < items.getSlots(); i++) {
                    ItemStack current = items.getStackInSlot(i);
                    if (current.getFoodProperties(player) != null) {
                        slotId = i;
                        break;
                    }
                }

                if (slotId > -1) {
                    ItemStack stack = items.getStackInSlot(slotId);
                    InteractionResultHolder<ItemStack> result = ArmorUtils.eat(player, stack);
                    stack = result.getObject();
                    if (stack.isEmpty())
                        items.setStackInSlot(slotId, ItemStack.EMPTY);

                    if (result.getResult() == InteractionResult.SUCCESS)
                        item.discharge(cost, item.getTier(), true, false, false);

                    return true;
                }
            }
        }
        return false;
    }

    public void removeNegativeEffects(@NotNull IElectricItem item, Player player) {
        if (player.getActiveEffects().isEmpty()) return;
        for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
            long cost = getEffectRemovalCost(effect);
            if (cost > 0 && item.canUse(cost)) {
                item.discharge(cost, item.getTier(), true, false, false);
                player.removeEffect(effect.getEffect());
            }
        }
    }

    /**
     * GTO: 清除该效果所需电量（按等级倍增），不可清除时返回 -1
     */
    public long getEffectRemovalCost(MobEffectInstance effect) {
        int parts = potionRemovalCost.getOrDefault(effect.getEffect(), -1);
        return parts < 0 ? -1 : ampHourParts((long) parts * (effect.getAmplifier() + 1));
    }

    /*
     * @Override
     * public ArmorProperties getProperties(EntityLivingBase player, @NotNull ItemStack armor, DamageSource source,
     * double damage, EntityEquipmentSlot equipmentSlot) {
     * int damageLimit = Integer.MAX_VALUE;
     * IElectricItem item = armor.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
     * if (item == null) {
     * return new ArmorProperties(0, 0, damageLimit);
     * }
     * if (energyPerUse > 0) {
     * damageLimit = (int) Math.min(damageLimit, 25.0D * item.getCharge() / (energyPerUse * 100.0D));
     * }
     *
     * if (source == DamageSource.FALL) {
     * if (SLOT == EntityEquipmentSlot.FEET) {
     * return new ArmorProperties(10, 1.0D, damageLimit);
     * }
     *
     * if (SLOT == EntityEquipmentSlot.LEGS) {
     * return new ArmorProperties(9, 0.8D, damageLimit);
     * }
     * }
     * return new ArmorProperties(8, getDamageAbsorption() * getAbsorption(armor), damageLimit);
     * }
     *
     * @Override
     * public boolean handleUnblockableDamage(EntityLivingBase entity, @NotNull ItemStack armor, DamageSource source,
     * double damage, EntityEquipmentSlot equipmentSlot) {
     * return source != DamageSource.FALL && source != DamageSource.DROWN && source != DamageSource.STARVE &&
     * source != DamageSource.OUT_OF_WORLD;
     * }
     */

    @Override
    public int damageArmor(LivingEntity entity, ItemStack itemStack, DamageSource source, int damage,
                           EquipmentSlot equipmentSlot) {
        IElectricItem item = GTCapabilityHelper.getElectricItem(itemStack);
        if (item != null) {
            item.discharge(ampHourParts(NanoMuscleSuite.DAMAGE_COST_PARTS) * damage, item.getTier(), true, false,
                    false);
        }
        return super.damageArmor(entity, itemStack, source, damage, equipmentSlot);
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        ItemStack currentChest = Minecraft.getInstance().player.getInventory().armor
                .get(EquipmentSlot.CHEST.getIndex());
        String armorTexture = "quark_tech_suite";
        if (currentChest.is(GTItems.QUANTUM_CHESTPLATE_ADVANCED.get())) armorTexture = "advanced_quark_tech_suite";
        return slot != EquipmentSlot.LEGS ?
                GTCEu.id(String.format("textures/armor/%s_1.png", armorTexture)) :
                GTCEu.id(String.format("textures/armor/%s_2.png", armorTexture));
    }

    @Override
    public double getDamageAbsorption() {
        return getSuiteDamageAbsorption();
    }

    // GTO: 胸甲 1.2 / 其余 1.0 -> 统一 3.0，整套 21.6 -> 60 护甲
    @Override
    public double getSuiteDamageAbsorption() {
        return 3.0D;
    }

    // GTO: 韧性四档 纳米 20 / 进阶纳米 24 / 夸克 28 / 进阶夸克 30（Apothic Attributes 下 30 为抗破甲上限）
    // 击退抗性四档 0 / 1/6 / 1/3 / 1/2，本系列每件额外 2 韧性、1/12 击退抗性
    @Override
    public float getSuiteExtraToughness() {
        return 2.0F;
    }

    @Override
    public float getSuiteExtraKnockbackResistance() {
        return 1.0F / 12;
    }

    @Override
    public float getHeatResistance() {
        return 0.5f;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void drawHUD(ItemStack item, GuiGraphics guiGraphics) {
        addCapacityHUD(item, this.HUD);
        if (type == ArmorItem.Type.CHESTPLATE && canUseEnergy(item, energyPerUse)) {
            JetpackChestHelper.addHudLines(item, this.HUD);
        }
        this.HUD.draw(guiGraphics);
        this.HUD.reset();
    }

    @Override
    public InteractionResultHolder<ItemStack> onRightClick(Level world, Player player, InteractionHand hand) {
        if (type == ArmorItem.Type.CHESTPLATE && player.isShiftKeyDown()) {
            return JetpackChestHelper.onShiftUse(world, player, hand);
        }
        return super.onRightClick(world, player, hand);
    }

    @Override
    protected void addFeatures(ItemStack itemStack, List<Component> features) {
        ArmorTooltips.addFeature(features, "damage_drain", ArmorTooltips.piecePassive(),
                ArmorTooltips.ampHours("per_damage", NanoMuscleSuite.DAMAGE_COST_PARTS));
        ArmorTooltips.addFeature(features, "ppe", ArmorTooltips.setPassive(itemStack, ArmorTooltips::isPPE), null);
        ArmorTooltips.addDetail(features, "detail.ppe");
        // GTO: 放射性材料危害要求四个部位均为防护装备，II 及以上每件都算
        ArmorTooltips.addFeature(features, "radiation", ArmorTooltips.setPassive(itemStack, ArmorTooltips::isPPE),
                null);
        ArmorTooltips.addDetail(features, "detail.radiation");
        if (type == ArmorItem.Type.HELMET) {
            ArmorTooltips.addFeature(features, "breath",
                    ArmorTooltips.piecePassive(ArmorTooltips.canUse(itemStack, ampHourParts(BREATH_COST_PARTS))),
                    ArmorTooltips.ampHours("per_use", BREATH_COST_PARTS));
            ArmorTooltips.addDetail(features, "detail.breath");
            ArmorTooltips.addFeature(features, "auto_eat",
                    ArmorTooltips.piecePassive(ArmorTooltips.canUse(itemStack, ampHourParts(FOOD_COST_PARTS))),
                    ArmorTooltips.ampHours("per_use", FOOD_COST_PARTS));
            ArmorTooltips.addDetail(features, "detail.auto_eat");
            ArmorTooltips.addFeature(features, "cleanse",
                    ArmorTooltips.piecePassive(
                            ArmorTooltips.canUse(itemStack, ampHourParts(potionRemovalCost.getInt(MobEffects.UNLUCK)))),
                    ArmorTooltips.cost("per_effect"));
            ArmorTooltips.addDetail(features, "detail.cleanse_costs",
                    potionRemovalCost.getInt(MobEffects.POISON), potionRemovalCost.getInt(MobEffects.WITHER),
                    potionRemovalCost.getInt(MobEffects.CONFUSION), potionRemovalCost.getInt(MobEffects.DIG_SLOWDOWN),
                    potionRemovalCost.getInt(MobEffects.MOVEMENT_SLOWDOWN), potionRemovalCost.getInt(MobEffects.UNLUCK));
            ArmorTooltips.addDetail(features, "detail.no_nightvision");
        } else if (type == ArmorItem.Type.CHESTPLATE) {
            ArmorTooltips.addFeature(features, "fire_immune", ArmorTooltips.piecePassive(), null);
            // 胸甲带 minecraft:freeze_immune_wearables 标签
            ArmorTooltips.addFeature(features, "freeze_immune", ArmorTooltips.piecePassive(), null);
            ArmorTooltips.addJetpackFeatures(itemStack, this, features);
        } else if (type == ArmorItem.Type.LEGGINGS) {
            ArmorTooltips.addSpeedFeature(itemStack, this, features);
        } else if (type == ArmorItem.Type.BOOTS) {
            ArmorTooltips.addFeature(features, "step_assist", ArmorTooltips.piecePassive(), null);
            ArmorTooltips.addDetail(features, "detail.step_assist");
            CompoundTag data = itemStack.getTag();
            ArmorTooltips.addFeature(features, "boosted_jump",
                    ArmorTooltips.keyToggle(KeyBind.BOOTS_ENABLE, data != null && data.getBoolean("boostedJump"),
                            ArmorTooltips.canUse(itemStack, ampHourParts(JUMP_COST_PARTS))),
                    ArmorTooltips.ampHours("per_jump", JUMP_COST_PARTS));
            ArmorTooltips.addDetail(features, "detail.boosted_jump");
        }
    }

    @Override
    public int getGrade() {
        return 3;
    }

    @Override
    public boolean isPPE() {
        return true;
    }

    public static boolean isQuarkChestplate(ItemStack stack) {
        return stack.getItem() instanceof ArmorComponentItem armor &&
                armor.getArmorLogic() instanceof QuarkTechSuite suite && suite.type == ArmorItem.Type.CHESTPLATE;
    }

    // GTO: 夸克高科胸甲 (III) 的喷气背包参数，介于进阶纳米 (II) 与进阶夸克 (IV) 之间

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
    public int getFlightEnergyPerTick() {
        return (int) ampsPerSecond(JetpackChestHelper.FLIGHT_AMPS);
    }

    // GTO: 护腿疾跑倍率 III 可切换到 4.5 倍
    @Override
    public int getMaxSpeedLevel() {
        return 3;
    }

    @Override
    public double getSprintSpeedModifier() {
        return 2.1D;
    }

    @Override
    public double getVerticalHoverSpeed() {
        return 0.42D;
    }

    @Override
    public double getVerticalHoverSlowSpeed() {
        return 0.0D;
    }

    @Override
    public double getVerticalAcceleration() {
        return 0.145D;
    }

    @Override
    public double getVerticalSpeed() {
        return 0.85D;
    }

    @Override
    public double getSidewaysSpeed() {
        return 0.2D;
    }

    @Nullable
    @Override
    public ParticleOptions getParticle() {
        return null;
    }
}
