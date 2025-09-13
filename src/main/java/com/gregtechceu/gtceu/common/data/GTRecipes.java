package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.addon.AddonFinder;
import com.gregtechceu.gtceu.api.data.chemical.material.ItemMaterialData;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags;
import com.gregtechceu.gtceu.data.recipe.MaterialInfoLoader;
import com.gregtechceu.gtceu.data.recipe.configurable.RecipeAddition;
import com.gregtechceu.gtceu.data.recipe.configurable.RecipeRemoval;
import com.gregtechceu.gtceu.data.recipe.generated.*;
import com.gregtechceu.gtceu.data.recipe.misc.*;
import com.gregtechceu.gtceu.data.recipe.serialized.chemistry.ChemistryRecipes;
import com.gregtechceu.gtceu.utils.collection.OpenCacheHashSet;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.ComposterBlock;

import java.util.Set;

public class GTRecipes {

    public static final Set<ResourceLocation> RECIPE_FILTERS = new OpenCacheHashSet<>();

    /*
     * Called on resource reload in-game.
     *
     * These methods are meant for recipes that cannot be reasonably changed by a Datapack,
     * such as "X Ingot -> 2 X Rods" types of recipes, that follow a pattern for many recipes.
     *
     * This should also be used for recipes that need
     * to respond to a config option in ConfigHolder.
     */
    public static void recipeAddition() {
        ComposterRecipes.addComposterRecipes(ComposterBlock.COMPOSTABLES::put);

        // Decomposition info loading
        ItemMaterialData.reinitializeMaterialData();
        MaterialInfoLoader.init();

        // com.gregtechceu.gtceu.data.recipe.generated.*
        for (Material material : GTCEuAPI.materialManager.getRegisteredMaterials()) {
            if (material.hasFlag(MaterialFlags.NO_UNIFICATION)) {
                continue;
            }

            DecompositionRecipeHandler.run(material);
            MaterialRecipeHandler.run(material);
            OreRecipeHandler.run(material);
            PartsRecipeHandler.run(material);
            PipeRecipeHandler.run(material);
            PolarizingRecipeHandler.run(material);
            RecyclingRecipeHandler.run(material);
            ToolRecipeHandler.run(material);
            WireCombiningHandler.run(material);
            WireRecipeHandler.run(material);
        }

        CustomToolRecipes.init();
        AirScrubberRecipes.init();
        ChemistryRecipes.init();
        MetaTileEntityMachineRecipeLoader.init();
        MiscRecipeLoader.init();
        VanillaStandardRecipes.init();
        WoodMachineRecipes.init();
        StoneMachineRecipes.init();
        CraftingRecipeLoader.init();
        FuelRecipes.init();
        FusionLoader.init();
        MachineRecipeLoader.init();
        AssemblerRecipeLoader.init();
        AssemblyLineLoader.init();
        BatteryRecipes.init();
        DecorationRecipes.init();

        CircuitRecipes.init();
        ComponentRecipes.init();
        MetaTileEntityLoader.init();

        // Config-dependent recipes
        RecipeAddition.init();

        // Must run recycling recipes very last
        RecyclingRecipes.init();
        ItemMaterialData.resolveItemMaterialInfos();
    }

    /*
     * Called on resource reload in-game, just before the above method.
     *
     * This is also where any recipe removals should happen.
     */
    public static void recipeRemoval() {
        RECIPE_FILTERS.clear();

        RecipeRemoval.init(RECIPE_FILTERS::add);
        AddonFinder.getAddons().forEach(addon -> addon.removeRecipes(RECIPE_FILTERS::add));
    }
}
