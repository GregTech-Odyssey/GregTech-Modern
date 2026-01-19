package com.gregtechceu.gtceu.core.mixins;

import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;

import net.minecraft.world.level.material.Fluid;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(Fluid.class)
public class FluidMixin implements FluidIngredient.Value {

    @Override
    public boolean gtceu$testFluid(Object o) {
        return o == this;
    }
}
