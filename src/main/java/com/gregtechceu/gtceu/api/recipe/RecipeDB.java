package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;

import com.fast.recipesearch.AbstractRecipeDB;
import com.fast.recipesearch.IntLongMap;
import com.fast.recipesearch.IntMapContainer;
import com.fast.recipesearch.RecipeSearcher;

import java.util.function.BiPredicate;

public class RecipeDB extends AbstractRecipeDB<GTRecipeDefinition> {

    protected RecipeSearcher<GTRecipeDefinition> searchContext = new RecipeSearcher<>();

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
        if (!serialRecipes.isEmpty()) {
            for (var recipe : serialRecipes) {
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
    protected IntLongMap extractIntMap(GTRecipeDefinition recipe) {
        var intMap = new IntLongMap();
        recipe.itemInputs.forEach(content -> recipe.recipeType.convertItem((ItemIngredient) content.inner, intMap));
        recipe.fluidInputs.forEach(content -> recipe.recipeType.convertFluid((FluidIngredient) content.inner, intMap));
        for (var expand : recipe.contentExpanders) {
            expand.extractInput(recipe, intMap);
        }
        for (var expand : recipe.tickContentExpanders) {
            expand.extractInput(recipe, intMap);
        }
        return intMap;
    }

    @Override
    protected void setRecipeContainer(GTRecipeDefinition gtRecipe, IntMapContainer intMapContainer) {
        gtRecipe.container = intMapContainer;
    }
}
