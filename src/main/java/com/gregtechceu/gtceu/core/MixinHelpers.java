package com.gregtechceu.gtceu.core;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorage;
import com.gregtechceu.gtceu.api.registry.registrate.forge.GTClientFluidTypeExtensions;

import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

public class MixinHelpers {

    public static void addFluidTexture(Material material, FluidStorage.FluidEntry value) {
        if (value != null) {
            IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(value.getFluid().get());
            if (extensions instanceof GTClientFluidTypeExtensions gtExtensions && value.getBuilder() != null) {
                gtExtensions.setFlowingTexture(value.getBuilder().flowing());
                gtExtensions.setStillTexture(value.getBuilder().still());
            }
        }
    }
}
