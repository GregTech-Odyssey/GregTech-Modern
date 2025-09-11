package com.gregtechceu.gtceu.common.item;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.item.ITagPrefixItem;
import com.gregtechceu.gtceu.common.item.tool.CoatedTurbineRotorBehaviour;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class GTTurbineItemCoated extends GTTurbineItem implements ITagPrefixItem {

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("gtocore.turbine_rotor.coated", CoatedTurbineRotorBehaviour.getCoatMaterial(stack).getLocalizedName()).append(super.getName(stack));
    }

    @Override
    protected void attachComponents() {
        attachComponents(new CoatedTurbineRotorBehaviour());
    }

    public GTTurbineItemCoated(Properties properties, TagPrefix tagPrefix, Material material) {
        super(properties, tagPrefix, material);
    }
}
