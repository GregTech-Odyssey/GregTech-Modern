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
        var mgc = CoatedTurbineRotorBehaviour.isCoatingMagical(stack);
        MutableComponent eff, pow;
        if (mgc && this instanceof CoatedTurbineRotorBehaviour ct) {
            var effValue = stack.getEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY) * 0.1f + 1f;
            eff = Component.translatable("metaitem.tool.tooltip.rotor.efficiency", (int) (effValue * (getRotorEfficiency(stack) - ct.getRotorBonusEfficiency(stack))))
                    .append(Component.empty().append(" + (").withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(Component.translatable("metaitem.tool.tooltip.rotor.coating_efficiency", ct.getRotorBonusEfficiency(stack))).withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(")").withStyle(ChatFormatting.LIGHT_PURPLE));
            if (stack.getEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY) > 0) {
                eff.append(Component.empty().append(" * (").withStyle(ChatFormatting.DARK_PURPLE)
                        .append(Component.translatable("metaitem.tool.tooltip.rotor.coating.ench.efficiency", FormattingUtil.formatNumber2Places(effValue * 100))).withStyle(ChatFormatting.DARK_PURPLE)
                        .append(")").withStyle(ChatFormatting.DARK_PURPLE));
            }
            var powValue = stack.getEnchantmentLevel(Enchantments.POWER_ARROWS) * 0.05f + 1f;
            pow = Component.translatable("metaitem.tool.tooltip.rotor.power", (int) (powValue * (getRotorPower(stack) - ct.getRotorBonusPower(stack))))
                    .append(Component.empty().append(" + (").withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(Component.translatable("metaitem.tool.tooltip.rotor.coating_power", ct.getRotorBonusPower(stack))).withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(")").withStyle(ChatFormatting.LIGHT_PURPLE));
            if (stack.getEnchantmentLevel(Enchantments.POWER_ARROWS) > 0) {
                pow.append(Component.empty().append(" * (").withStyle(ChatFormatting.DARK_PURPLE)
                        .append(Component.translatable("metaitem.tool.tooltip.rotor.coating.ench.power", FormattingUtil.formatNumber2Places(powValue * 100))).withStyle(ChatFormatting.DARK_PURPLE)
                        .append(")").withStyle(ChatFormatting.DARK_PURPLE));
            }
        } else {
            eff = Component.translatable("metaitem.tool.tooltip.rotor.efficiency", getRotorEfficiency(stack));
            pow = Component.translatable("metaitem.tool.tooltip.rotor.power", getRotorPower(stack));
        }
        tooltipComponents.add(Tips.stat(eff));
        tooltipComponents.add(Tips.stat(pow));
        if (mgc && stack.getEnchantmentLevel(Enchantments.UNBREAKING) > 0) {
            var value = 100f / (stack.getEnchantmentLevel(Enchantments.UNBREAKING) + 1);
            tooltipComponents.add(
                    Component.translatable("metaitem.tool.tooltip.rotor.coating.ench.unbreaking", FormattingUtil.formatNumber2Places(value)).withStyle(ChatFormatting.DARK_PURPLE));

        }
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
