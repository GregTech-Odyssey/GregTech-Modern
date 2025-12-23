package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import com.fast.recipesearch.AbstractContainerRecipeDB;
import com.fast.recipesearch.IntLongMap;
import com.fast.recipesearch.RecipeSearcher;

import java.util.List;
import java.util.function.Function;

public class RecipeDB extends AbstractContainerRecipeDB<GTRecipe> {

    public RecipeDB(List<Runnable> branchBuilder) {
        super(branchBuilder);
    }

    @Override
    public GTRecipe findAnyMatch(int[] searchKeys, Function<GTRecipe, GTRecipe> recipeProcessor) {
        searchContext.reset(this.rootBranch, searchKeys, recipeProcessor, null);
        GTRecipe foundRecipe = searchContext.findAny();
        if (foundRecipe != null) {
            return foundRecipe;
        } else {
            return !this.serialRecipes.isEmpty() ? findInSerial(this.serialRecipes, recipeProcessor) : null;
        }
    }

    @Override
    protected IntLongMap extractIntMap(GTRecipe recipe) {
        var intMap = new IntLongMap();
        recipe.inputs.forEach((cap, contents) -> {
            for (Content content : contents) {
                if (recipe.recipeType != null) {
                    recipe.recipeType.convert(cap, content.content, intMap);
                } else {
                    GTRecipeTypes.DUMMY_RECIPES.convert(cap, content.content, intMap);
                }
            }
        });
        return intMap;
    }

    @Override
    public void clear() {
        this.searchContext = new RecipeSearcher<>();
    }
}
