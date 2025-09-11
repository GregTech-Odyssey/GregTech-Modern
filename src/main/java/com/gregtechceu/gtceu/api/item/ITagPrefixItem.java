package com.gregtechceu.gtceu.api.item;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ITagPrefixItem {

    @OnlyIn(Dist.CLIENT)
    static ItemColor tintColor(Material material) {
        return (itemStack, index) -> material.getLayerARGB(index);
    }

    TagPrefix getTagPrefix();

    Material getMaterial();

    default void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltipComponents,
                                 TooltipFlag isAdvanced) {
        if (this.getTagPrefix().tooltip() != null) {
            this.getTagPrefix().tooltip().accept(getMaterial(), tooltipComponents);
        }
    }

    default String getDescriptionId() {
        return getTagPrefix().getUnlocalizedName(getMaterial());
    }

    default String getDescriptionId(ItemStack stack) {
        return getTagPrefix().getUnlocalizedName(getMaterial());
    }

    default Component getDescription() {
        return getTagPrefix().getLocalizedName(getMaterial());
    }

    default Component getName(ItemStack stack) {
        return getDescription();
    }
}
