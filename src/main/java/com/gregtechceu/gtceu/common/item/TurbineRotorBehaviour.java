package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.component.IMaterialPartItem;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TurbineRotorBehaviour implements IMaterialPartItem {

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

    public void applyRotorDamage(ItemStack itemStack, int damageApplied) {
        int rotorDurability = getPartMaxDurability(itemStack);
        int resultDamage = getPartDamage(itemStack) + damageApplied;
        if (resultDamage >= rotorDurability) {
            itemStack.shrink(1);
        } else {
            setPartDamage(itemStack, resultDamage);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable Level level,
                                List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        IMaterialPartItem.super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
        tooltipComponents
                .add(Component.translatable("metaitem.tool.tooltip.rotor.efficiency", getRotorEfficiency(stack)));
        tooltipComponents.add(Component.translatable("metaitem.tool.tooltip.rotor.power", getRotorPower(stack)));
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
