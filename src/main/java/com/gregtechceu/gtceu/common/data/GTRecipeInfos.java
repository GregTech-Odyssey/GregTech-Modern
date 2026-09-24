package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.api.addon.AddonFinder;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.recipe.info.*;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

public class GTRecipeInfos {

    public final static ItemRecipeInfo ITEM = ItemRecipeInfo.INSTANCE;
    public final static FluidRecipeInfo FLUID = FluidRecipeInfo.INSTANCE;
    public final static EURecipeInfo EU = EURecipeInfo.INSTANCE;
    public final static CWURecipeInfo CWU = CWURecipeInfo.INSTANCE;
    public final static ContentRecipeInfo<?, ?>[] CONTENT_RECIPE_INFOS;

    static {
        GTRegistries.RECIPE_INFOS.unfreeze();

        GTRegistries.RECIPE_INFOS.register(ITEM.name, ITEM);
        GTRegistries.RECIPE_INFOS.register(FLUID.name, FLUID);
        GTRegistries.RECIPE_INFOS.register(EU.name, EU);
        GTRegistries.RECIPE_INFOS.register(CWU.name, CWU);

        AddonFinder.getAddons().forEach(IGTAddon::registerRecipeCapabilities);
        GTRegistries.RECIPE_INFOS.freeze();
        CONTENT_RECIPE_INFOS = GTRegistries.RECIPE_INFOS.values().stream().filter(i -> i instanceof ContentRecipeInfo<?, ?>).toArray(ContentRecipeInfo[]::new);
    }

    public static void init() {}
}
