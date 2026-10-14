package com.gregtechceu.gtceu.api.item.tool;

import com.gregtechceu.gtceu.api.item.tool.aoe.AoESymmetrical;
import com.gregtechceu.gtceu.api.item.tool.behavior.IToolBehavior;
import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * 工具类型的数值与行为声明，所有字段在声明时定死，物品逻辑直接读字段。
 */
public final class ToolDefinition {

    public final IToolBehavior[] behaviors;
    public final int damagePerAction;
    public final int damagePerCraftingAction;
    public final boolean blockBreaking;
    public final boolean attacking;
    public final boolean crafting;
    public final boolean canAttack;
    public final int baseDurability;
    public final float durabilityMultiplier;
    public final int baseQuality;
    public final float attackDamage;
    public final float baseEfficiency;
    public final float efficiencyMultiplier;
    public final float attackSpeed;
    public final boolean sneakBypassUse;
    public final AoESymmetrical aoe;
    public final ToolSetting[] settings;
    @Nullable
    private final EnchantmentCategory[] enchantmentCategories;
    private final Object2IntMap<Enchantment> defaultEnchantments;
    @Nullable
    private final Supplier<ItemStack> brokenStack;

    private ToolDefinition(Builder b) {
        this.behaviors = b.behaviors.toArray(IToolBehavior[]::new);
        this.damagePerAction = b.damagePerAction;
        this.damagePerCraftingAction = b.damagePerCraftingAction;
        this.blockBreaking = b.blockBreaking;
        this.attacking = b.attacking;
        this.crafting = b.crafting;
        this.canAttack = b.canAttack;
        this.baseDurability = b.baseDurability;
        this.durabilityMultiplier = b.durabilityMultiplier;
        this.baseQuality = b.baseQuality;
        this.attackDamage = b.attackDamage;
        this.baseEfficiency = b.baseEfficiency;
        this.efficiencyMultiplier = b.efficiencyMultiplier;
        this.attackSpeed = b.attackSpeed;
        this.sneakBypassUse = b.sneakBypassUse;
        this.aoe = b.aoe;
        List<ToolSetting> settings = new ArrayList<>();
        for (IToolBehavior behavior : behaviors) {
            Collections.addAll(settings, behavior.getSettings());
        }
        this.settings = settings.isEmpty() ? ToolSetting.NONE : settings.toArray(ToolSetting[]::new);
        this.enchantmentCategories = b.enchantmentCategories;
        this.defaultEnchantments = Object2IntMaps.unmodifiable(b.defaultEnchantments);
        this.brokenStack = b.brokenStack;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean hasBehavior(IToolBehavior behavior) {
        for (IToolBehavior b : behaviors) {
            if (b == behavior) return true;
        }
        return false;
    }

    public int damagePerBlockBreak() {
        return blockBreaking ? damagePerAction : damagePerAction * 2;
    }

    public int damagePerAttack() {
        return attacking ? damagePerAction : damagePerAction * 2;
    }

    public int damagePerCraft() {
        return crafting ? damagePerCraftingAction : damagePerCraftingAction * 2;
    }

    public boolean canApplyEnchantment(Enchantment enchantment) {
        if (enchantmentCategories == null) return false;
        for (EnchantmentCategory category : enchantmentCategories) {
            if (enchantment.category == category) return true;
        }
        return false;
    }

    public Object2IntMap<Enchantment> defaultEnchantments() {
        return defaultEnchantments;
    }

    public ItemStack brokenStack() {
        return brokenStack == null ? ItemStack.EMPTY : brokenStack.get();
    }

    public static final class Builder {

        private final List<IToolBehavior> behaviors = new ArrayList<>();
        private int damagePerAction = 1;
        private int damagePerCraftingAction = 1;
        private boolean blockBreaking;
        private boolean attacking;
        private boolean crafting;
        private boolean canAttack = true;
        private int baseDurability;
        private float durabilityMultiplier = 1.0F;
        private int baseQuality;
        private float attackDamage = 1.0F;
        private float baseEfficiency = 4.0F;
        private float efficiencyMultiplier = 1.0F;
        private float attackSpeed;
        private boolean sneakBypassUse;
        private AoESymmetrical aoe = AoESymmetrical.ZERO;
        @Nullable
        private EnchantmentCategory[] enchantmentCategories;
        private final Object2IntMap<Enchantment> defaultEnchantments = new Object2IntArrayMap<>();
        @Nullable
        private Supplier<ItemStack> brokenStack;

        private Builder() {}

        public Builder behaviors(IToolBehavior... behaviors) {
            Collections.addAll(this.behaviors, behaviors);
            return this;
        }

        public Builder blockBreaking() {
            this.blockBreaking = true;
            return this;
        }

        public Builder attacking() {
            this.attacking = true;
            return this;
        }

        public Builder crafting() {
            this.crafting = true;
            return this;
        }

        public Builder cannotAttack() {
            this.canAttack = false;
            return this;
        }

        public Builder canApplyEnchantment(EnchantmentCategory... categories) {
            this.enchantmentCategories = categories;
            return this;
        }

        public Builder sneakBypassUse() {
            this.sneakBypassUse = true;
            return this;
        }

        public Builder aoe(int additionalColumns, int additionalRows, int additionalDepth) {
            this.aoe = AoESymmetrical.of(additionalColumns, additionalRows, additionalDepth);
            return this;
        }

        public Builder defaultEnchantment(Enchantment enchantment, int level) {
            if (ConfigHolder.INSTANCE.recipes.enchantedTools) {
                this.defaultEnchantments.put(enchantment, level);
            }
            return this;
        }

        public Builder damagePerAction(int damagePerAction) {
            this.damagePerAction = damagePerAction;
            return this;
        }

        public Builder damagePerCraftingAction(int damagePerCraftingAction) {
            this.damagePerCraftingAction = damagePerCraftingAction;
            return this;
        }

        public Builder baseDurability(int baseDurability) {
            this.baseDurability = baseDurability;
            return this;
        }

        public Builder durabilityMultiplier(float durabilityMultiplier) {
            this.durabilityMultiplier = durabilityMultiplier;
            return this;
        }

        public Builder baseQuality(int baseQuality) {
            this.baseQuality = baseQuality;
            return this;
        }

        public Builder attackDamage(float attackDamage) {
            this.attackDamage = attackDamage;
            return this;
        }

        public Builder baseEfficiency(float baseEfficiency) {
            this.baseEfficiency = baseEfficiency;
            return this;
        }

        public Builder efficiencyMultiplier(float efficiencyMultiplier) {
            this.efficiencyMultiplier = efficiencyMultiplier;
            return this;
        }

        public Builder attackSpeed(float attackSpeed) {
            this.attackSpeed = attackSpeed;
            return this;
        }

        public Builder brokenStack(Supplier<ItemStack> brokenStack) {
            this.brokenStack = brokenStack;
            return this;
        }

        ToolDefinition build() {
            return new ToolDefinition(this);
        }
    }
}
