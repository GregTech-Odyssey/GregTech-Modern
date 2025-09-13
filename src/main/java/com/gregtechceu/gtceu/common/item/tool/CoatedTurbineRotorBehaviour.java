package com.gregtechceu.gtceu.common.item.tool;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.item.component.IMaterialPartItem;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.item.TurbineRotorBehaviour;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.gregtechceu.gtceu.common.item.GTTurbineItem.getRotorMaxDamage;

public class CoatedTurbineRotorBehaviour extends TurbineRotorBehaviour implements IMaterialPartItem {

    private final RandomSource rd = RandomSource.create();

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, level, tooltipComponents, isAdvanced);
        tooltipComponents
                .add(Component.translatable("metaitem.tool.tooltip.rotor.coating", getCoatMaterial(stack).getLocalizedName()));
        // 机制说明标题
        tooltipComponents
                .add(Component.translatable("metaitem.tool.tooltip.rotor.coating.tooltip.0"));
        // 当转子A有镀层B，镀层B的耐久为max(B/10, min(B, A + B/2))，
        tooltipComponents
                .add(Component.translatable("metaitem.tool.tooltip.rotor.coating.tooltip.1"));
        // 每次转子损坏时95%概率优先消耗镀层耐久，镀层耐久耗尽后再消耗本体耐久
        tooltipComponents
                .add(Component.translatable("metaitem.tool.tooltip.rotor.coating.tooltip.2"));
    }

    public static Material getCoatMaterial(ItemStack itemStack) {
        var compound = getPartStatsTag(itemStack);
        var defaultMaterial = GTMaterials.Neutronium;
        if (compound == null || !compound.contains("Coating", Tag.TAG_STRING)) {
            return defaultMaterial;
        }
        var materialName = compound.getString("Coating");
        var material = GTMaterials.get(materialName);
        if (material.isNull() || !material.hasProperty(PropertyKey.INGOT)) {
            return defaultMaterial;
        }
        return material;
    }

    public static void setCoatMaterial(ItemStack itemStack, Material coating) {
        var behaviour = getBehaviour(itemStack);
        if (!(behaviour instanceof CoatedTurbineRotorBehaviour)) {
            return;
        }
        var compound = getOrCreatePartStatsTag(itemStack);
        compound.putString("Coating", coating.getResourceLocation().toString());

        var bonusDurability = getRotorMaxDamage(coating);
        var originalDurability = behaviour.getPartMaxDurability(itemStack);
        var result = Math.min(bonusDurability, originalDurability + bonusDurability / 2) - originalDurability;
        compound.putInt("ProtectionMaxDamage", Math.max(result, bonusDurability / 10));
    }

    public static CompoundTag getPartStatsTag(ItemStack itemStack) {
        return itemStack.getTagElement("GT.PartStats");
    }

    public int getCoatDamage(ItemStack itemStack) {
        var compound = getPartStatsTag(itemStack);
        if (compound == null || !compound.contains("ProtectionDamage", Tag.TAG_ANY_NUMERIC)) {
            return 0;
        }
        return compound.getInt("ProtectionDamage");
    }

    public int getCoatMaxDamage(ItemStack itemStack) {
        var compound = getPartStatsTag(itemStack);
        if (compound == null || !compound.contains("ProtectionMaxDamage", Tag.TAG_ANY_NUMERIC)) {
            return 0;
        }
        return compound.getInt("ProtectionMaxDamage");
    }

    public void setCoatDamage(ItemStack itemStack, int resultDamage) {
        var compound = getOrCreatePartStatsTag(itemStack);
        compound.putInt("ProtectionDamage", resultDamage);
    }

    static CompoundTag getOrCreatePartStatsTag(ItemStack itemStack) {
        return itemStack.getOrCreateTagElement("GT.PartStats");
    }

    /**
     * 优先消耗涂层耐久（95%概率），涂层耐久耗尽后再消耗本体耐久
     */
    @Override
    public void setPartDamage(ItemStack itemStack, int resultDamage) {
        if (resultDamage > getDamage(itemStack) &&
                getCoatDamage(itemStack) < getCoatMaxDamage(itemStack) &&
                rd.nextInt(100) < 95) {
            setCoatDamage(itemStack, (int) Math.min(getCoatDamage(itemStack) + resultDamage - getDamage(itemStack), getCoatMaxDamage(itemStack)));
            return;
        }
        super.setPartDamage(itemStack, resultDamage);
    }
}
