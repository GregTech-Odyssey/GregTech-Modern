package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.component.ITurbineRotorBehavior;
import com.gregtechceu.gtceu.common.item.tool.CoatedTurbineRotorBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
        MutableComponent eff, pow;
        if (this instanceof CoatedTurbineRotorBehaviour ct && CoatedTurbineRotorBehaviour.isCoatingMagical(stack)) {
            eff = Component.translatable("metaitem.tool.tooltip.rotor.efficiency", getRotorEfficiency(stack) - ct.getRotorBonusEfficiency(stack))
                    .append(Component.empty().append(" + (").withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(Component.translatable("metaitem.tool.tooltip.rotor.coating_efficiency", ct.getRotorBonusEfficiency(stack))).withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(")").withStyle(ChatFormatting.LIGHT_PURPLE));
            pow = Component.translatable("metaitem.tool.tooltip.rotor.power", getRotorPower(stack) - ct.getRotorBonusPower(stack))
                    .append(Component.empty().append(" + (").withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(Component.translatable("metaitem.tool.tooltip.rotor.coating_power", ct.getRotorBonusPower(stack))).withStyle(ChatFormatting.LIGHT_PURPLE)
                            .append(")").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            eff = Component.translatable("metaitem.tool.tooltip.rotor.efficiency", getRotorEfficiency(stack));
            pow = Component.translatable("metaitem.tool.tooltip.rotor.power", getRotorPower(stack));
        }
        tooltipComponents.add(eff);
        tooltipComponents.add(pow);
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
