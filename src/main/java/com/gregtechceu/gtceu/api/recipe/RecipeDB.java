package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.ContentRecipeCapability;

import com.fast.recipesearch.AbstractRecipeDB;
import com.fast.recipesearch.IntLongMap;
import com.fast.recipesearch.IntMapContainer;
import com.fast.recipesearch.RecipeSearcher;

import java.util.function.Predicate;

public class RecipeDB extends AbstractRecipeDB<GTRecipeDefinition> {

    protected RecipeSearcher<GTRecipeDefinition> searchContext = new RecipeSearcher<>();

    public RecipeDB() {
        super();
    }

    public boolean search(IntLongMap map, Predicate<GTRecipeDefinition> canHandle) {
        if (rootBranch != null) {
            searchContext.reset(maxSearchDepth, rootBranch, map, map.toIntArray(), r -> r.container.match(map) && canHandle.test(r), null);
            if (searchContext.findAny() != null) {
                return true;
            }
        }
        if (!serialRecipes.isEmpty()) {
            for (var recipe : serialRecipes) {
                if (canHandle.test(recipe)) return true;
            }
        }
        return false;
    }

    @Override
    protected boolean supportsParallel(GTRecipeDefinition recipe) {
        return false;
    }

    @Override
    protected IntLongMap extractIntMap(GTRecipeDefinition recipe) {
        var intMap = new IntLongMap();
        recipe.inputs.forEach((cap, contents) -> {
            if (cap instanceof ContentRecipeCapability<?> capability) contents.forEach(content -> recipe.recipeType.convert(capability, content.inner, intMap));
        });
        return intMap;
    }

    @Override
    protected void setRecipeContainer(GTRecipeDefinition gtRecipe, IntMapContainer intMapContainer) {
        gtRecipe.container = intMapContainer;
    }
}
