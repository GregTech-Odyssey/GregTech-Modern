package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentInner;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.util.RandomSource;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RecipeHelper {

    public static final RandomSource RNG = RandomSource.create();

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

    /**
     * Creates a copy of the recipe matching the trim limits -
     * Returns the recipe itself if no valid trim limits are passed
     */
    public static void trimRecipeOutputs(GTRecipe recipe, Reference2IntOpenHashMap<RecipeInfo> trimLimits) {
        if (trimLimits.isEmpty()) return;
        recipe.itemOutputs = doTrim(recipe.itemOutputs, ItemRecipeInfo.INSTANCE, trimLimits);
        recipe.fluidOutputs = doTrim(recipe.fluidOutputs, FluidRecipeInfo.INSTANCE, trimLimits);
    }

    /**
     * Returns the maximum possible recipe outputs from a recipe, divided into regular and chanced outputs
     * Takes into account any specific output limiters, ie macerator slots, to trim down the output list
     * Trims from chanced outputs first, then regular outputs
     *
     * @param trimLimits The limit(s) on the number of outputs
     * @return All recipe outputs, limited by some factor(s)
     */
    public static <T extends ContentInner> List<Content<T>> doTrim(List<Content<T>> contents,
                                                                   RecipeInfo capability, Reference2IntOpenHashMap<RecipeInfo> trimLimits) {
        if (contents.isEmpty()) return contents;
        return trimFirst(contents, trimLimits.getOrDefault(capability, -1));
    }

    public static <T extends ContentInner> List<Content<T>> trimFirst(List<Content<T>> contents, int trimLimit) {
        int N = Math.min(contents.size(), trimLimit);
        if (N == 0) return Collections.emptyList();
        if (N == -1) return contents;
        var array = new Content[N];
        for (var i = 0; i < N; i++) {
            array[i] = contents.get(i);
        }
        return Arrays.asList(array);
    }

    public static <T extends ContentInner> List<Content<T>> trimLast(List<Content<T>> contents, int trimLimit) {
        int size = contents.size();
        int N = Math.min(size, trimLimit);
        if (N == 0) return Collections.emptyList();
        if (N == -1) return contents;
        var array = new Content[N];
        for (int i = 0; i < N; i++) {
            array[i] = contents.get(size - N + i);
        }
        return Arrays.asList(array);
    }

    public static <T extends ContentInner> List<Content<T>> trimRange(List<Content<T>> contents, int fromIndex, int toIndex) {
        int size = contents.size();
        if (fromIndex < 0) fromIndex = 0;
        if (toIndex > size) toIndex = size;
        if (fromIndex >= toIndex) return Collections.emptyList();
        var array = new Content[toIndex - fromIndex];
        for (int i = fromIndex; i < toIndex; i++) {
            array[i - fromIndex] = contents.get(i);
        }
        return Arrays.asList(array);
    }

    public static <T extends ContentInner> List<Content<T>> copyAndRoll(GTRecipe recipe, List<Content<T>> contents) {
        var size = contents.size();
        if (size == 0) return Collections.emptyList();
        var contentList = new ArrayList<Content<T>>(size);
        var boost = recipe.definition.chanceFunction;
        var recipeTier = recipe.tier;
        var chanceTier = recipeTier + recipe.ocLevel;
        for (var content : contents) {
            if (content.chance == Content.MAX_CHANCE) {
                contentList.add(content.copy());
            } else {
                if (content.chance == 0) continue;
                var inner = content.inner;
                long chance = (long) (((double) content.amount / inner.amount) * boost.getBoostedChance(content, recipeTier, chanceTier)) + RNG.nextInt(Content.MAX_CHANCE);
                long multiplier = chance / Content.MAX_CHANCE;
                if (multiplier > 0) {
                    contentList.add(new Content<>(content, inner.amount * multiplier));
                }
            }
        }
        return contentList;
    }

    public static <T extends ContentInner> List<Content<T>> modifierContents(List<Content<T>> contents, @Range(from = 1, to = ParallelLogic.MAX_PARALLEL) long multiplier) {
        if (multiplier == 1) return contents;
        var size = contents.size();
        if (size == 0) return contents;
        var array = new Content[size];
        for (var i = 0; i < size; i++) {
            array[i] = contents.get(i).modifier(multiplier);
        }
        return Arrays.asList(array);
    }

    public static <T extends ContentInner> List<Content<T>> copyContents(List<Content<T>> contents, @Range(from = 1, to = ParallelLogic.MAX_PARALLEL) long multiplier) {
        var size = contents.size();
        if (size == 0) return Collections.emptyList();
        var list = new ArrayList<Content<T>>(size);
        for (Content<T> content : contents) {
            list.add(content.copy(multiplier));
        }
        return list;
    }
}
