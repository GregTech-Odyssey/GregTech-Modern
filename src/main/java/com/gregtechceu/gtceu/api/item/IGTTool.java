package com.gregtechceu.gtceu.api.item;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.ToolProperty;
import com.gregtechceu.gtceu.api.item.capability.ElectricItem;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.item.tool.ToolData;
import com.gregtechceu.gtceu.api.item.tool.ToolDefinition;
import com.gregtechceu.gtceu.api.item.tool.ToolSetting;
import com.gregtechceu.gtceu.api.item.tool.aoe.AoESymmetrical;
import com.gregtechceu.gtceu.api.item.tool.behavior.IToolBehavior;
import com.gregtechceu.gtceu.api.sound.SoundEntry;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public interface IGTTool extends HeldItemUIFactory.IHeldItemUIHolder, ItemLike {

    GTToolType getToolType();

    Material getMaterial();

    ToolProperty getToolProperty();

    long getEnergyPerUse(ItemStack stack);

    default ToolDefinition getToolStats() {
        return getToolType().toolDefinition;
    }

    default boolean isElectric() {
        return getToolType().isElectric();
    }

    default int getElectricTier() {
        return getToolType().electricTier;
    }

    default Set<GTToolType> getToolClasses(ItemStack stack) {
        return getToolType().toolClasses;
    }

    @Nullable
    default SoundEntry getSound() {
        return getToolType().soundEntry;
    }

    default boolean playSoundOnBlockDestroy() {
        return getToolType().playSoundOnBlockDestroy;
    }

    default Item asItem() {
        return (Item) this;
    }

    default ItemStack getRaw() {
        return new ItemStack(asItem());
    }

    default ItemStack get() {
        ItemStack stack = new ItemStack(asItem());
        IDataComponentItem.set(stack, ToolData.DISALLOW_CONTAINER_ITEM, false);
        IDataComponentItem.set(stack, ToolData.HIDE_FLAGS, 2);
        ToolData.setDamage(stack, 0);
        ToolProperty toolProperty = getToolProperty();
        if (toolProperty.isUnbreakable()) {
            IDataComponentItem.set(stack, ToolData.UNBREAKABLE, true);
        }
        ToolDefinition definition = getToolStats();
        var enchantments = new Reference2IntOpenHashMap<>(toolProperty.getEnchantments());
        enchantments.putAll(definition.defaultEnchantments());
        for (Reference2IntMap.Entry<Enchantment> entry : enchantments.reference2IntEntrySet()) {
            if (entry.getKey().canEnchant(stack)) {
                stack.enchant(entry.getKey(), entry.getIntValue());
            }
        }
        for (IToolBehavior behavior : definition.behaviors) {
            behavior.addBehaviorNBT(stack);
        }
        if (!definition.aoe.isZero()) {
            IDataComponentItem.set(stack, AoESymmetrical.KEY, definition.aoe);
        }
        if (isElectric()) {
            ElectricItem electricItem = (ElectricItem) GTCapabilityHelper.getElectricItem(stack);
            ItemStack powerUnit = definition.brokenStack();
            var unit = GTCapabilityHelper.getElectricItem(powerUnit);
            if (electricItem != null && unit != null) {
                electricItem.setMaxChargeOverride(unit.getMaxCharge());
            }
        }
        return stack;
    }

    default ItemStack get(long defaultCharge, long defaultMaxCharge) {
        ItemStack stack = get();
        if (isElectric()) {
            ElectricItem electricItem = (ElectricItem) GTCapabilityHelper.getElectricItem(stack);
            if (electricItem != null) {
                electricItem.setMaxChargeOverride(defaultMaxCharge);
                electricItem.setCharge(defaultCharge);
            }
        }
        return stack;
    }

    default ItemStack get(long defaultMaxCharge) {
        return get(defaultMaxCharge, defaultMaxCharge);
    }

    default boolean isMagnetic() {
        return getToolProperty().isMagnetic();
    }

    default Material getToolMaterial(ItemStack stack) {
        return stack.getItem() instanceof IGTTool tool ? tool.getMaterial() : GTMaterials.Iron;
    }

    default float getMaterialToolSpeed(ItemStack stack) {
        return getToolProperty().getHarvestSpeed();
    }

    default float getMaterialAttackDamage(ItemStack stack) {
        return getToolProperty().getAttackDamage();
    }

    default float getMaterialAttackSpeed(ItemStack stack) {
        return getToolProperty().getAttackSpeed();
    }

    default int getMaterialDurability(ItemStack stack) {
        ToolProperty property = getToolProperty();
        return property.getDurability() * property.getDurabilityMultiplier();
    }

    default int getMaterialEnchantability(ItemStack stack) {
        return getToolProperty().getEnchantability();
    }

    default int getMaterialHarvestLevel(ItemStack stack) {
        return getToolProperty().getHarvestLevel();
    }

    default long getMaxCharge(ItemStack stack) {
        return isElectric() && stack.hasTag() ? IDataComponentItem.getLong(stack, ElectricItem.MAX_CHARGE) : -1L;
    }

    default long getCharge(ItemStack stack) {
        return isElectric() && stack.hasTag() ? IDataComponentItem.getLong(stack, ElectricItem.CHARGE) : -1L;
    }

    default float getTotalToolSpeed(ItemStack stack) {
        ToolDefinition definition = getToolStats();
        return definition.efficiencyMultiplier * getMaterialToolSpeed(stack) + definition.baseEfficiency;
    }

    default float getTotalAttackDamage(ItemStack stack) {
        ToolDefinition definition = getToolStats();
        return definition.canAttack ? getMaterialAttackDamage(stack) + definition.attackDamage : 0F;
    }

    default float getTotalAttackSpeed(ItemStack stack) {
        return getMaterialAttackSpeed(stack) + getToolStats().attackSpeed;
    }

    default int getTotalMaxDurability(ItemStack stack) {
        ToolDefinition definition = getToolStats();
        int materialDurability = getMaterialDurability(stack);
        int builderDurability = (int) (definition.baseDurability * definition.durabilityMultiplier);
        return builderDurability == 0 ? (int) (materialDurability * definition.durabilityMultiplier) :
                materialDurability + builderDurability;
    }

    default int getTotalEnchantability(ItemStack stack) {
        return getMaterialEnchantability(stack);
    }

    default int getTotalHarvestLevel(ItemStack stack) {
        return getMaterialHarvestLevel(stack) + getToolStats().baseQuality;
    }

    default void playCraftingSound(@Nullable Player player, ItemStack stack) {
        if (ConfigHolder.INSTANCE.client.toolCraftingSounds && getSound() != null && player != null) {
            playSound(player);
        }
    }

    default void playSound(Player player) {
        if (player.level().isClientSide) return;
        SoundEntry sound = getSound();
        if (ConfigHolder.INSTANCE.client.toolUseSounds && sound != null && canPlaySound(player)) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound.getMainEvent(),
                    SoundSource.PLAYERS, 1F, 1F);
        }
    }

    @Override
    default ModularUI createUI(Player player, HeldItemUIFactory.HeldItemHolder holder) {
        return ToolSetting.createUI(player, holder);
    }

    @OnlyIn(Dist.CLIENT)
    static ItemColor tintColor() {
        return (itemStack, index) -> {
            if (itemStack.getItem() instanceof IGTTool item) {
                Material material = item.getMaterial();
                return switch (index) {
                    case 0, -101 -> {
                        if (item.getToolType().actsAs(GTToolType.CROWBAR)) {
                            Integer tint = IDataComponentItem.get(itemStack, ToolData.TINT_COLOR);
                            if (tint != null) yield tint;
                        }
                        yield -1;
                    }
                    case 1, -111 -> material.getMaterialARGB();
                    case 2, -121 -> material.getMaterialSecondaryARGB() != -1 ? material.getMaterialSecondaryARGB() :
                            material.getMaterialARGB();
                    default -> -1;
                };
            }
            return -1;
        };
    }

    Map<UUID, Long> PLAYED_SOUNDS = new ConcurrentHashMap<>();

    static boolean canPlaySound(@Nullable Player player) {
        if (player == null) return false;
        long currentTime = System.currentTimeMillis();
        Long lastTime = PLAYED_SOUNDS.get(player.getUUID());
        if (lastTime == null || Math.abs(currentTime - lastTime) > 100) {
            PLAYED_SOUNDS.put(player.getUUID(), currentTime);
            return true;
        }
        return false;
    }
}
