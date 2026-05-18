package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentInner;
import com.gregtechceu.gtceu.utils.GTUtil;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecipeHelper {

    public static long getRealEUt(@NotNull GTRecipe recipe) {
        long EUt = recipe.getInputEUt();
        if (EUt > 0) return EUt;
        return -recipe.getOutputEUt();
    }

    public static int getRecipeEUtTier(GTRecipe recipe) {
        long EUt = recipe.getInputEUt();
        if (EUt == 0) EUt = recipe.getOutputEUt();
        if (recipe.parallels > 1) EUt /= recipe.parallels;
        return GTUtil.getTierByVoltage(EUt);
    }

    public static int getRecipeEUtTier(GTRecipeDefinition recipe) {
        long EUt = recipe.getInputEUt();
        if (EUt == 0) EUt = recipe.getOutputEUt();
        return GTUtil.getTierByVoltage(EUt);
    }

    public static int getPreOCRecipeEuTier(GTRecipe recipe) {
        long EUt = recipe.getInputEUt();
        if (EUt == 0) EUt = recipe.getOutputEUt();
        if (recipe.parallels > 1) EUt /= recipe.parallels;
        EUt >>= (recipe.ocLevel * 2);
        return GTUtil.getTierByVoltage(EUt);
    }

    /**
     * Creates a copy of the recipe matching the trim limits -
     * Returns the recipe itself if no valid trim limits are passed
     */
    @Contract(pure = true)
    public static void trimRecipeOutputs(GTRecipe recipe, Reference2IntOpenHashMap<RecipeCapability<?>> trimLimits) {
        if (trimLimits.isEmpty()) return;
        recipe.itemOutputs = doTrim(recipe.itemOutputs, ItemRecipeCapability.CAP, trimLimits);
        recipe.fluidOutputs = doTrim(recipe.fluidOutputs, FluidRecipeCapability.CAP, trimLimits);
    }

    /**
     * Returns the maximum possible recipe outputs from a recipe, divided into regular and chanced outputs
     * Takes into account any specific output limiters, ie macerator slots, to trim down the output list
     * Trims from chanced outputs first, then regular outputs
     *
     * @param trimLimits The limit(s) on the number of outputs
     * @return All recipe outputs, limited by some factor(s)
     */
    @Contract(pure = true)
    public static <T extends ContentInner> List<Content<T>> doTrim(List<Content<T>> contents,
                                                                   RecipeCapability<T> capability, Reference2IntOpenHashMap<RecipeCapability<?>> trimLimits) {
        if (contents.isEmpty()) return contents;
        int N = trimLimits.getOrDefault(capability, -1);
        if (N == -1) return contents;
        if (N == 0) return Collections.emptyList();
        List<Content<T>> list = new ArrayList<>();
        int added = 0;
        for (var content : contents) {
            if (added == N) break;
            list.add(content);
            added++;
        }
        return list;
    }
}
