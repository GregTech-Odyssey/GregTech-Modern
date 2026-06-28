package com.gregtechceu.gtceu.common.item.armor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.item.armor.ArmorLogicSuite;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NightvisionGoggles extends ArmorLogicSuite {

    public NightvisionGoggles(int energyPerUse, long capacity, int voltageTier, ArmorItem.Type slot) {
        super(energyPerUse, capacity, voltageTier, slot);
    }

    @Override
    public void onArmorTick(Level world, @NotNull Player player, @NotNull ItemStack itemStack) {}

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return GTCEu.id("textures/armor/nightvision_goggles.png");
    }

    @Override
    public void addInfo(ItemStack itemStack, List<Component> lines) {
        super.addInfo(itemStack, lines);
        if (type == ArmorItem.Type.HELMET) {
            lines.add(Component.translatable("metaarmor.tooltip.gto_no_nightvision"));
        }
    }
}
