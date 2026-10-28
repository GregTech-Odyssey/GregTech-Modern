package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.component.ITurbineRotorBehavior;
import com.gregtechceu.gtceu.api.tooltip.Tips;
import com.gregtechceu.gtceu.common.item.tool.CoatedTurbineRotorBehaviour;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("NoTranslation")
public class TurbineRotorBehaviour implements ITurbineRotorBehavior {

    public int getRotorPower(ItemStack stack) {
        return stack.getItem() instanceof GTTurbineItem t ? t.getPower() : -1;
    }

    public int getRotorEfficiency(ItemStack stack) {
        return stack.getItem() instanceof GTTurbineItem t ? t.getEfficiency() : -1;
    }

    @Override
    public int getPartMaxDurability(ItemStack itemStack) {
        return itemStack.getMaxDamage();
    }

    // 把手伸进去所造成的伤害
    public float getDamage(ItemStack stack) {
        return stack.getItem() instanceof GTTurbineItem t ? t.getDoDamageToEntity() : 0;
    }

    public int getRotorDurabilityPercent(ItemStack itemStack) {
        return 100 - 100 * getPartDamage(itemStack) / getPartMaxDurability(itemStack);
    }

    public ItemStack applyRotorDamage(ItemStack itemStack, int damageApplied) {
        int rotorDurability = getPartMaxDurability(itemStack);
        int resultDamage = getPartDamage(itemStack) + damageApplied;
        if (resultDamage >= rotorDurability) {
            itemStack.shrink(1);
        } else {
            setPartDamage(itemStack, resultDamage);
        }
        return itemStack;
    }

    @Override
    public void appendTooltips(ItemStack stack, @org.jetbrains.annotations.Nullable Level level,
                               List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        ITurbineRotorBehavior.super.appendTooltips(stack, level, tooltipComponents, isAdvanced);
        boolean mgc = CoatedTurbineRotorBehaviour.isCoatingMagical(stack);
        int baseEfficiency = stack.getItem() instanceof GTTurbineItem t ? t.getEfficiency() : -1;
        int basePower = stack.getItem() instanceof GTTurbineItem t ? t.getPower() : -1;
        int bonusEfficiency = 0, bonusPower = 0;
        double efficiencyMultiplier = 1, powerMultiplier = 1;
        if (this instanceof CoatedTurbineRotorBehaviour ct) {
            bonusEfficiency = ct.getRotorBonusEfficiency(stack);
            bonusPower = ct.getRotorBonusPower(stack);
            efficiencyMultiplier += ct.getEnchBonusEfficiency(stack);
            powerMultiplier += ct.getEnchBonusPower(stack);
        }
        tooltipComponents.add(Tips.stat(Component.translatable("metaitem.tool.tooltip.rotor.total_efficiency",
                percent(getRotorEfficiency(stack), ChatFormatting.WHITE))));
        if (bonusEfficiency != 0 || efficiencyMultiplier != 1) {
            tooltipComponents.add(Tips.stat(rotorFormula(baseEfficiency, bonusEfficiency, efficiencyMultiplier)));
        }
        tooltipComponents.add(Tips.stat(Component.translatable("metaitem.tool.tooltip.rotor.total_power",
                percent(getRotorPower(stack), ChatFormatting.WHITE))));
        if (bonusPower != 0 || powerMultiplier != 1) {
            tooltipComponents.add(Tips.stat(rotorFormula(basePower, bonusPower, powerMultiplier)));
        }
        if (mgc && stack.getEnchantmentLevel(Enchantments.UNBREAKING) > 0) {
            var value = 100f / (stack.getEnchantmentLevel(Enchantments.UNBREAKING) + 1);
            tooltipComponents.add(
                    Tips.stat(Component.translatable("metaitem.tool.tooltip.rotor.coating.ench.unbreaking", FormattingUtil.formatNumber2Places(value))));

        }
    }

    private static MutableComponent percent(int value, ChatFormatting color) {
        return Component.literal(Integer.toString(value)).append("%").withStyle(color);
    }

    private static Component rotorFormula(int base, int bonus, double multiplier) {
        boolean enchanted = multiplier != 1;
        MutableComponent formula = Component.literal(enchanted && bonus != 0 ? " = (" : " = ").withStyle(ChatFormatting.GRAY)
                .append(percent(base, ChatFormatting.GRAY));
        if (bonus != 0) {
            formula.append(Component.literal(" + ").withStyle(ChatFormatting.GRAY))
                    .append(percent(bonus, ChatFormatting.LIGHT_PURPLE));
        }
        if (enchanted) {
            formula.append(Component.literal(bonus != 0 ? ") × " : " × ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(FormattingUtil.formatNumber2Places(multiplier * 100)).append("%")
                            .withStyle(ChatFormatting.DARK_PURPLE));
        }
        return formula;
    }

    @Nullable
    public static TurbineRotorBehaviour getBehaviour(@NotNull ItemStack itemStack) {
        if (itemStack.getItem() instanceof IComponentItem componentItem) {
            for (var component : componentItem.getComponents()) {
                if (component instanceof TurbineRotorBehaviour behaviour) {
                    return behaviour;
                }
            }
        }
        return null;
    }
}
