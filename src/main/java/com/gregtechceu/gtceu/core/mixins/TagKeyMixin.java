package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(TagKey.class)
public class TagKeyMixin implements FluidIngredient.Value {

    @Override
    public boolean gtceu$testFluid(Object o) {
        Object tag = this;
        return ((Fluid) o).is((TagKey) tag);
    }
}
