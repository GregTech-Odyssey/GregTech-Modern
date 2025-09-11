package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.component.*;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Deprecated
public class TurbineRotorBehaviourLegacy implements IMaterialPartItem {

    public int getRotorPower(ItemStack stack) {
        var property = getPartMaterial(stack).getProperty(PropertyKey.ROTOR);
        return property == null ? -1 : property.getPower();
    }

    public int getRotorEfficiency(ItemStack stack) {
        var property = getPartMaterial(stack).getProperty(PropertyKey.ROTOR);
        return property == null ? -1 : property.getEfficiency();
    }

    @Override
    public int getPartMaxDurability(ItemStack itemStack) {
        var property = getPartMaterial(itemStack).getProperty(PropertyKey.ROTOR);
        return property == null ? -1 : 800 * (int) Math.pow(property.getDurability(), 0.65);
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

    @Override
    public Material getPartMaterial(ItemStack itemStack) {
        var compound = getPartStatsTag(itemStack);
        var defaultMaterial = GTMaterials.Neutronium;
        if (compound == null || !compound.contains("Material", Tag.TAG_STRING)) {
            return defaultMaterial;
        }
        var materialName = compound.getString("Material");
        var material = GTMaterials.get(materialName);
        if (material.isNull() || !material.hasProperty(PropertyKey.INGOT)) {
            return defaultMaterial;
        }
        return material;
    }

    CompoundTag getPartStatsTag(ItemStack itemStack) {
        return itemStack.getTagElement("GT.PartStats");
    }

    @Override
    public int getPartDamage(ItemStack itemStack) {
        var compound = getPartStatsTag(itemStack);
        if (compound == null || !compound.contains("Damage", Tag.TAG_ANY_NUMERIC)) {
            return 0;
        }
        return compound.getInt("Damage");
    }

    @Override
    public void setPartDamage(ItemStack itemStack, int damage) {
        var compound = getOrCreatePartStatsTag(itemStack);
        compound.putInt("Damage", Math.min(getPartMaxDurability(itemStack), damage));
    }

    CompoundTag getOrCreatePartStatsTag(ItemStack itemStack) {
        return itemStack.getOrCreateTagElement("GT.PartStats");
    }
}
