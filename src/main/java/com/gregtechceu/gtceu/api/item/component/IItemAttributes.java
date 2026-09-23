package com.gregtechceu.gtceu.api.item.component;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

import com.google.common.collect.Multimap;

public interface IItemAttributes {

    Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack);

    /**
     * GTO: 返回 true 时隐藏原版"在主手时"属性段（物品已在自己的 tooltip 中写明数值）
     */
    default boolean hideAttributeTooltip(ItemStack stack) {
        return false;
    }
}
