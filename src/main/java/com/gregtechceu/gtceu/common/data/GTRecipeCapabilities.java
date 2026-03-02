package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

public class GTRecipeCapabilities {

    public static void init() {
        GTRegistries.RECIPE_CAPABILITIES.unfreeze();
        GTRegistries.RECIPE_CAPABILITIES.register(ItemRecipeCapability.CAP.name, ItemRecipeCapability.CAP);
        GTRegistries.RECIPE_CAPABILITIES.register(FluidRecipeCapability.CAP.name, FluidRecipeCapability.CAP);
        GTRegistries.RECIPE_CAPABILITIES.freeze();
    }
}
