package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import com.gto.recipesearch.*;

import java.util.function.BiPredicate;

public final class RecipeDB extends AbstractRecipeDB<GTRecipeDefinition> {

    RecipeSearcher<GTRecipeDefinition> searchContext = new RecipeSearcher<>();

    public RecipeDB() {
        super();
    }

    public boolean search(RecipeHandlerUnit unit, IntLongMap map, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> canHandle) {
        if (rootBranch != null) {
            searchContext.reset(maxSearchDepth, rootBranch, map, map.toIntArray(), r -> r.container.match(map) && canHandle.test(unit, r), null);
            if (searchContext.findAny() != null) {
                return true;
            }
        }
        if (!unindexedSerial.isEmpty()) {
            for (var recipe : unindexedSerial) {
                if (canHandle.test(unit, recipe)) return true;
            }
        }
        return false;
    }

    @Override
    protected boolean supportsParallel(GTRecipeDefinition recipe) {
        return false;
    }

    @Override
    protected IntLongMap extractIngredientMap(GTRecipeDefinition recipe) {
        var intMap = new IntLongMap();
        recipe.itemInputs.forEach(content -> recipe.recipeType.convertItem(content.inner, intMap));
        recipe.fluidInputs.forEach(content -> recipe.recipeType.convertFluid(content.inner, intMap));
        for (var extension : recipe.recipeExtensions) {
            extension.extractInput(recipe, intMap);
        }
        for (var extension : recipe.tickRecipeExtensions) {
            extension.extractInput(recipe, intMap);
        }
        return intMap;
    }

    @Override
    protected void setIngredientTable(GTRecipeDefinition gtRecipe, IngredientTable intMapContainer) {
        gtRecipe.container = intMapContainer;
    }
}
