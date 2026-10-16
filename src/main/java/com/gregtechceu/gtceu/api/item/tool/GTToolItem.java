package com.gregtechceu.gtceu.api.item.tool;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.CombinedCapabilityProvider;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.item.IElectricItem;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.ToolProperty;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.item.IDataComponentItem;
import com.gregtechceu.gtceu.api.item.IGTTool;
import com.gregtechceu.gtceu.api.item.capability.ElectricItem;
import com.gregtechceu.gtceu.api.item.component.ElectricStats;
import com.gregtechceu.gtceu.api.item.component.forge.IComponentCapability;
import com.gregtechceu.gtceu.api.item.tool.behavior.IToolBehavior;
import com.gregtechceu.gtceu.api.tooltip.ElectricText;
import com.gregtechceu.gtceu.api.tooltip.TipRole;
import com.gregtechceu.gtceu.api.tooltip.Tips;
import com.gregtechceu.gtceu.client.renderer.item.ToolItemRenderer;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 全部 GT 工具共用的物品类，差异全部来自 {@link GTToolType} 声明。
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class GTToolItem extends DiggerItem implements IGTTool {

    private static final String[] HARVEST_LEVEL_KEYS = {
            "item.gtceu.tool.harvest_level.0", "item.gtceu.tool.harvest_level.1", "item.gtceu.tool.harvest_level.2",
            "item.gtceu.tool.harvest_level.3", "item.gtceu.tool.harvest_level.4", "item.gtceu.tool.harvest_level.5",
            "item.gtceu.tool.harvest_level.6" };

    protected final GTToolType toolType;
    protected final Material material;
    protected final ToolProperty toolProperty;
    private int maxDamage = -1;
    private float ironToolSpeed = -1;
    private Multimap<Attribute, AttributeModifier> attributeModifiers;

    protected GTToolItem(GTToolType toolType, MaterialToolTier tier, Material material, Properties properties) {
        super(0, 0, tier, toolType.harvestTags.isEmpty() ? null : toolType.harvestTags.getFirst(), properties);
        this.toolType = toolType;
        this.material = material;
        this.toolProperty = material.getProperty(PropertyKey.TOOL);
        if (GTCEu.isClientSide()) {
            ToolItemRenderer.create(this, toolType);
        }
    }

    public static GTToolItem create(GTToolType toolType, MaterialToolTier tier, Material material, Properties properties) {
        return new GTToolItem(toolType, tier, material, properties);
    }

    @Override
    public GTToolType getToolType() {
        return toolType;
    }

    @Override
    public Material getMaterial() {
        return material;
    }

    @Override
    public ToolProperty getToolProperty() {
        return toolProperty;
    }

    @Override
    public long getEnergyPerUse(ItemStack stack) {
        return energyForSpeed(getTotalToolSpeed(stack));
    }

    protected final long energyForSpeed(float speed) {
        long base = ConfigHolder.INSTANCE.machines.energyUsageMultiplier;
        float iron = ironToolSpeed();
        if (iron <= 0 || !getToolStats().blockBreaking) return base;
        double ratio = speed / iron;
        return Math.max(1, Math.round(base * ratio * ratio));
    }

    private float ironToolSpeed() {
        if (ironToolSpeed < 0) {
            ToolProperty iron = GTMaterials.Iron.getProperty(PropertyKey.TOOL);
            ToolDefinition definition = getToolStats();
            ironToolSpeed = iron == null ? 0 : definition.efficiencyMultiplier * iron.getHarvestSpeed() + definition.baseEfficiency;
        }
        return ironToolSpeed;
    }

    @Override
    public ItemStack getDefaultInstance() {
        return get();
    }

    @Override
    public String getDescriptionId() {
        return toolType.descriptionId;
    }

    @Override
    public Component getDescription() {
        return Component.translatable(toolType.descriptionId, material.getLocalizedName());
    }

    @Override
    public Component getName(ItemStack stack) {
        return getDescription();
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        return toolType.toolActions.contains(toolAction);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !toolType.actsAsSword() || !player.isCreative();
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (toolType.actsAsSword() && state.getBlock() instanceof WebBlock) return 15.0F;
        return ToolHelper.isToolEffective(state, toolType, getTotalHarvestLevel(stack)) ? getTotalToolSpeed(stack) : 1.0F;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return ToolHelper.isToolEffective(state, toolType, getTotalHarvestLevel(stack));
    }

    public float getAttackBonus(ItemStack stack, LivingEntity target) {
        float bonus = 0;
        for (IToolBehavior behavior : getToolStats().behaviors) {
            bonus += behavior.getAttackBonus(stack, target);
        }
        return bonus;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        ToolHelper.damageItem(stack, attacker, getToolStats().damagePerAttack());
        return true;
    }

    @Override
    public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        for (IToolBehavior behavior : getToolStats().behaviors) {
            behavior.onBlockStartBreak(stack, pos, player);
        }
        if (player.isShiftKeyDown()) return false;
        int result = toolType.actsAs(GTToolType.SHEARS) ? ToolHelper.shearBlockRoutine(serverPlayer, stack, pos) : -1;
        if (result == 0 || !toolType.isEffectiveOn(player.level().getBlockState(pos))) return false;
        if (ToolHelper.areaOfEffectBlockBreakRoutine(stack, serverPlayer, pos) || result == -1) {
            if (playSoundOnBlockDestroy()) playSound(player);
            return false;
        }
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miningEntity) {
        if (level.isClientSide) return true;
        ToolDefinition definition = getToolStats();
        for (IToolBehavior behavior : definition.behaviors) {
            behavior.onBlockDestroyed(stack, level, state, pos, miningEntity);
        }
        if (state.getDestroySpeed(level, pos) != 0.0F) {
            ToolHelper.damageItem(stack, miningEntity, definition.damagePerBlockBreak());
        }
        if (miningEntity instanceof Player player && playSoundOnBlockDestroy() && player.isShiftKeyDown()) {
            playSound(player);
        }
        return true;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        for (IToolBehavior behavior : getToolStats().behaviors) {
            InteractionResult result = behavior.onItemUseFirst(stack, context);
            if (result.consumesAction()) return result;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        for (IToolBehavior behavior : getToolStats().behaviors) {
            InteractionResult result = behavior.onItemUse(context);
            if (result != InteractionResult.PASS) return result;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (ToolSetting.canOpenUI(player, hand) &&
                getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE).getType() != HitResult.Type.BLOCK) {
            if (player instanceof ServerPlayer serverPlayer) HeldItemUIFactory.INSTANCE.openUI(serverPlayer, hand);
            return InteractionResultHolder.success(held);
        }
        for (IToolBehavior behavior : getToolStats().behaviors) {
            if (behavior.onItemRightClick(level, player, hand).getResult() == InteractionResult.SUCCESS) {
                return InteractionResultHolder.success(held);
            }
        }
        return InteractionResultHolder.pass(held);
    }

    public boolean shouldOpenUIAfterUse(UseOnContext context) {
        for (IToolBehavior behavior : getToolStats().behaviors) {
            if (!behavior.shouldOpenUIAfterUse(context)) return false;
        }
        return true;
    }

    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker) {
        for (IToolBehavior behavior : getToolStats().behaviors) {
            if (behavior.canDisableShield(stack, shield, entity, attacker)) return true;
        }
        return false;
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return getToolStats().sneakBypassUse;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if (stack.isEmpty()) return false;
        switch (enchantment.getDescriptionId()) {
            case "enchantment.cofhcore.smashing":
                return false;
            case "enchantment.autosmelt":
            case "enchantment.cofhcore.smelting":
            case "enchantment.as.smelting":
                return getToolStats().aoe.isZero();
        }
        ToolDefinition definition = getToolStats();
        switch (enchantment.category) {
            case DIGGER -> {
                return definition.blockBreaking;
            }
            case WEAPON -> {
                return definition.attacking;
            }
            case BREAKABLE -> {
                return !ToolData.isUnbreakable(stack);
            }
        }
        if (toolProperty.getEnchantments().containsKey(enchantment)) return true;
        return definition.canApplyEnchantment(enchantment);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return getTotalEnchantability(stack);
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        if (toRepair.getDamageValue() == 0) return false;
        if (repair.getItem() instanceof IGTTool tool) {
            return material == tool.getMaterial();
        }
        MaterialEntry entry = ChemicalHelper.getMaterialEntry(repair.getItem());
        if (entry.isEmpty() || entry.material() != material) return false;
        if (VanillaRecipeHelper.isMaterialWood(entry.material())) {
            return entry.tagPrefix() == TagPrefix.planks;
        }
        if (entry.tagPrefix() == TagPrefix.plate) return true;
        if (entry.material().hasProperty(PropertyKey.INGOT)) return entry.tagPrefix() == TagPrefix.ingot;
        if (entry.material().hasProperty(PropertyKey.GEM)) return entry.tagPrefix() == TagPrefix.gem;
        return false;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) return ImmutableMultimap.of();
        var modifiers = attributeModifiers;
        if (modifiers == null) {
            modifiers = attributeModifiers = ImmutableMultimap.of(
                    Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier",
                            getTotalAttackDamage(stack), AttributeModifier.Operation.ADDITION),
                    Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier",
                            Math.max(-3.9D, getTotalAttackSpeed(stack)), AttributeModifier.Operation.ADDITION));
        }
        return modifiers;
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        if (!oldStack.is(newStack.getItem())) return true;
        CompoundTag newTag = newStack.getTag();
        CompoundTag oldTag = oldStack.getTag();
        if (newTag == null || oldTag == null) return newTag != oldTag;
        return !sameIgnoringWear(newTag, oldTag) || !sameIgnoringWear(oldTag, newTag);
    }

    private static boolean sameIgnoringWear(CompoundTag a, CompoundTag b) {
        for (String key : a.getAllKeys()) {
            if (key.equals(ElectricItem.CHARGE_KEY)) continue;
            Tag value = a.get(key);
            Tag other = b.get(key);
            if (key.equals(ToolData.TOOL_TAG_KEY) && value instanceof CompoundTag tool && other instanceof CompoundTag otherTool) {
                for (String toolKey : tool.getAllKeys()) {
                    if (toolKey.equals(ItemStack.TAG_DAMAGE)) continue;
                    if (!Objects.equals(tool.get(toolKey), otherTool.get(toolKey))) return false;
                }
                continue;
            }
            if (!Objects.equals(value, other)) return false;
        }
        return true;
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return !IDataComponentItem.getBoolean(stack, ToolData.DISALLOW_CONTAINER_ITEM);
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        if (!hasCraftingRemainingItem(stack)) return ItemStack.EMPTY;
        stack = stack.copy();
        Player player = ForgeHooks.getCraftingPlayer();
        ToolHelper.damageItemWhenCrafting(stack, player);
        playCraftingSound(player, stack);
        return stack.isEmpty() ? getToolStats().brokenStack() : stack;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (getCharge(oldStack) != getCharge(newStack)) return slotChanged;
        return !oldStack.equals(newStack);
    }

    @Override
    public boolean isDamaged(ItemStack stack) {
        return getDamage(stack) > 0;
    }

    @Override
    public int getDamage(ItemStack stack) {
        return ToolData.damage(stack);
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        int max = maxDamage;
        if (max < 0) max = maxDamage = getTotalMaxDurability(stack);
        return max;
    }

    @Override
    public void setDamage(ItemStack stack, int damage) {
        ToolData.setDamage(stack, damage);
    }

    @Override
    @Nullable
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        List<ICapabilityProvider> providers = null;
        for (IToolBehavior behavior : getToolStats().behaviors) {
            if (behavior instanceof IComponentCapability capability) {
                if (providers == null) providers = new ArrayList<>(1);
                providers.add(new ICapabilityProvider() {

                    @Override
                    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
                        return capability.getCapability(stack, cap);
                    }
                });
            }
        }
        if (providers == null) return null;
        return providers.size() == 1 ? providers.getFirst() : new CombinedCapabilityProvider(providers);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ToolDefinition definition = getToolStats();
        IElectricItem electricItem = isElectric() ? GTCapabilityHelper.getElectricItem(stack) : null;
        boolean breakable = !ToolData.isUnbreakable(stack);
        boolean details = GTUtil.isShiftDown();
        if (electricItem != null) {
            ElectricStats.addCurrentChargeTooltip(tooltip, electricItem.getCharge(), electricItem.getMaxCharge(), getElectricTier());
            if (details) {
                tooltip.add(Tips.detail(Component.translatable("item.gtceu.tool.tooltip.energy_per_use", ElectricText.energyExact(getEnergyPerUse(stack)))));
            }
        }
        int maxDurability = getMaxDamage(stack);
        if (breakable) {
            tooltip.add(Tips.durability(maxDurability - stack.getDamageValue(), maxDurability));
            if (details) {
                tooltip.add(Tips.detail(Component.translatable("gtceu.tip.detail.durability")));
                if (definition.crafting) {
                    tooltip.add(Tips.detail(Component.translatable("item.gtceu.tool.tooltip.damage_per_craft", definition.damagePerCraft())));
                }
                if (electricItem != null) {
                    tooltip.add(Tips.detail(Component.translatable("item.gtceu.tool.tooltip.charged_durability_saving",
                            100 - ConfigHolder.INSTANCE.tools.rngDamageElectricTools)));
                }
            }
        }
        if (electricItem != null) {
            ElectricStats.addBatteryTooltip(tooltip, stack, electricItem.getMaxCharge(), getElectricTier(), true);
        }
        if (definition.attacking) {
            tooltip.add(Tips.stat(Component.translatable("item.gtceu.tool.tooltip.attack_damage",
                    FormattingUtil.formatNumbers(1 + getTotalAttackDamage(stack)))));
            tooltip.add(Tips.stat(Component.translatable("item.gtceu.tool.tooltip.attack_speed",
                    FormattingUtil.formatNumbers(4 + getTotalAttackSpeed(stack)))));
        }
        if (definition.blockBreaking) {
            tooltip.add(Tips.stat(Component.translatable("item.gtceu.tool.tooltip.mining_speed",
                    FormattingUtil.formatNumbers(getTotalToolSpeed(stack)))));
            int harvestLevel = getTotalHarvestLevel(stack);
            if (harvestLevel >= 0 && harvestLevel < HARVEST_LEVEL_KEYS.length) {
                tooltip.add(Tips.stat(Component.translatable("item.gtceu.tool.tooltip.harvest_level_extra", harvestLevel,
                        Component.translatable(HARVEST_LEVEL_KEYS[harvestLevel]))));
            } else {
                tooltip.add(Tips.stat(Component.translatable("item.gtceu.tool.tooltip.harvest_level", harvestLevel)));
            }
        }
        tooltip.add(Tips.stat(Component.translatable("item.gtceu.tool.usable_as", toolType.usableAs())));
        int features = tooltip.size();
        for (String key : toolType.tooltipKeys) {
            tooltip.add(Tips.feature(Component.translatable(key)));
        }
        if (isMagnetic()) {
            tooltip.add(Tips.feature(Component.translatable("item.gtceu.tool.behavior.relocate_mining")));
            if (details) tooltip.add(Tips.detail(Component.translatable("item.gtceu.tool.behavior.relocate_mining.detail")));
        }
        for (IToolBehavior behavior : definition.behaviors) {
            behavior.addInformation(stack, level, tooltip, flag);
        }
        if (toolType.toolHead() != null) {
            tooltip.add(Tips.feature(Component.translatable("item.gtceu.tool.replace_tool_head")));
        }
        if (tooltip.size() > features) {
            tooltip.add(features, Tips.heading(Component.translatable("item.gtceu.tool.tooltip.features")));
        }
        ToolSetting.addSettings(stack, definition.settings, tooltip, details);
        ToolSetting.addControls(stack, definition.settings, tooltip);
        if (details) {
            Component repair = breakable ? repairMaterials() : null;
            if (repair != null) {
                tooltip.add(Tips.stat(Component.translatable("item.gtceu.tool.tooltip.repair_material", repair)));
            }
        } else {
            tooltip.add(Tips.line(TipRole.FOOTER, Component.translatable("gtceu.tooltip.hold_shift")));
        }
    }

    @Nullable
    private Component repairMaterials() {
        Component ingot = null;
        if (!VanillaRecipeHelper.isMaterialWood(material)) {
            if (material.hasProperty(PropertyKey.INGOT)) {
                ingot = TagPrefix.ingot.getLocalizedName(material);
            } else if (material.hasProperty(PropertyKey.GEM)) {
                ingot = TagPrefix.gem.getLocalizedName(material);
            }
        }
        Component plate = ChemicalHelper.getItem(TagPrefix.plate, material) != Items.AIR ?
                TagPrefix.plate.getLocalizedName(material) : null;
        if (ingot == null) return plate;
        if (plate == null) return ingot;
        MutableComponent combined = ingot.copy();
        FormattingUtil.combineComponents(combined, plate);
        return combined;
    }
}
