package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.api.addon.AddonFinder;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.recipe.info.*;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

public class GTRecipeInfos {

    public final static RecipeInfo ITEM = ItemRecipeInfo.INSTANCE;
    public final static RecipeInfo FLUID = FluidRecipeInfo.INSTANCE;
    public final static RecipeInfo EU = EURecipeInfo.INSTANCE;
    public final static RecipeInfo CWU = CWURecipeInfo.INSTANCE;

    public static void init() {
        GTRegistries.RECIPE_INFOS.unfreeze();

        GTRegistries.RECIPE_INFOS.register(ITEM.name, ITEM);
        GTRegistries.RECIPE_INFOS.register(FLUID.name, FLUID);
        GTRegistries.RECIPE_INFOS.register(EU.name, EU);
        GTRegistries.RECIPE_INFOS.register(CWU.name, CWU);

        AddonFinder.getAddons().forEach(IGTAddon::registerRecipeCapabilities);
        GTRegistries.RECIPE_INFOS.freeze();
    }
}
