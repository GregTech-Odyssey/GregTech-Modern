package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.content.ContentRoll;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.util.RandomSource;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;

public class RecipeHelper {

    public static final RandomSource RNG = ContentRoll.RNG;

    public static int getRecipeEUtTier(GTRecipe recipe) {
        long EUt = recipe.getInputEUt();
        if (EUt == 0) EUt = recipe.getOutputEUt();
        return GTUtil.getTierByVoltage(EUt);
    }

    public static int getRecipeEUtTier(GTRecipeDefinition recipe) {
        long EUt = recipe.getInputEUt();
        if (EUt == 0) EUt = recipe.getOutputEUt();
        return GTUtil.getTierByVoltage(EUt);
    }

    public static void trimRecipeOutputs(GTRecipe recipe, Reference2IntOpenHashMap<RecipeInfo> trimLimits) {
        if (trimLimits.isEmpty()) return;
        recipe.itemOutputs = recipe.itemOutputs.trimFirst(trimLimits.getOrDefault(ItemRecipeInfo.INSTANCE, -1));
        recipe.fluidOutputs = recipe.fluidOutputs.trimFirst(trimLimits.getOrDefault(FluidRecipeInfo.INSTANCE, -1));
    }
}
