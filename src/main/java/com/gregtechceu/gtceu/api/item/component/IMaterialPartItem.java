package com.gregtechceu.gtceu.api.item.component;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.item.GTTurbineItem;
import com.gregtechceu.gtceu.common.item.tool.CoatedTurbineRotorBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface IMaterialPartItem extends IDurabilityBar, IAddInformation, ICustomDescriptionId {

    int getPartMaxDurability(ItemStack itemStack);

    default Material getPartMaterial(ItemStack itemStack) {
        var defaultMaterial = GTMaterials.Neutronium;
        return itemStack.getItem() instanceof GTTurbineItem t ? t.getMaterial() : defaultMaterial;
    }

    default int getPartDamage(ItemStack itemStack) {
        return itemStack.getDamageValue();
    }

    default void setPartDamage(ItemStack itemStack, int damage) {
        itemStack.setDamageValue(damage);
    }

    @Override
    @Nullable
    default Component getItemName(ItemStack stack) {
        var material = getPartMaterial(stack);
        return Component.translatable(stack.getDescriptionId(), material.getLocalizedName());
    }

    @Override
    default void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable Level level,
                                 List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        var material = getPartMaterial(stack);
        var maxDurability = getPartMaxDurability(stack);
        var damage = getPartDamage(stack);
        MutableComponent c = Component.translatable("metaitem.tool.tooltip.durability", maxDurability - damage, maxDurability);
        if (this instanceof CoatedTurbineRotorBehaviour ct) {
            var coatMaxDmg = ct.getCoatMaxDamage(stack);
            var coatDmg = ct.getCoatDamage(stack);
            c.append(Component.empty().append(" + (").withStyle(ChatFormatting.GREEN)
                    .append(Component.translatable("metaitem.tool.tooltip.rotor.coating_durability", coatMaxDmg - coatDmg, coatMaxDmg)).withStyle(ChatFormatting.GREEN)
                    .append(")").withStyle(ChatFormatting.GREEN));
        }
        tooltipComponents
                .add(c);
        tooltipComponents
                .add(Component.translatable("metaitem.tool.tooltip.primary_material", material.getLocalizedName()));
    }

    @OnlyIn(Dist.CLIENT)
    static ItemColor getItemStackColor() {
        return (itemStack, i) -> {
            if (itemStack.getItem() instanceof IComponentItem componentItem) {
                for (IItemComponent component : componentItem.getComponents()) {
                    if (component instanceof IMaterialPartItem materialPartItem) {
                        return materialPartItem.getPartMaterial(itemStack).getMaterialARGB();
                    }
                }
            }
            return -1;
        };
    }

    @Override
    default float getDurabilityForDisplay(ItemStack itemStack) {
        var maxDurability = getPartMaxDurability(itemStack);
        return (float) (maxDurability - getPartDamage(itemStack)) / maxDurability;
    }

    @Override
    default int getMaxDurability(ItemStack stack) {
        return getPartMaxDurability(stack);
    }
}
