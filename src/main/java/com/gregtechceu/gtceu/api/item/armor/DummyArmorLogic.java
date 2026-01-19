package com.gregtechceu.gtceu.api.item.armor;

import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;

public class DummyArmorLogic implements IArmorLogic {

    @Override
    public ArmorItem.Type getArmorType() {
        return ArmorItem.Type.HELMET;
    }

    @Override
    public int getArmorDisplay(Player player, @NotNull ItemStack armor, EquipmentSlot slot) {
        return 0;
    }

    @Override
    public boolean isValidArmor(ItemStack itemStack, Entity entity, EquipmentSlot equipmentSlot) {
        return false;
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return GTUtil.getResourceLocation("minecraft", "textures/armor/diamond_layer_0.png");
    }
}
