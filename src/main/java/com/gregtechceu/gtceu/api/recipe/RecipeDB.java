package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import com.fast.recipesearch.AbstractContainerRecipeDB;
import com.fast.recipesearch.IntLongMap;
import com.fast.recipesearch.RecipeSearcher;

import java.util.List;
import java.util.function.Predicate;

public class RecipeDB extends AbstractContainerRecipeDB<GTRecipe> {

    public RecipeDB(List<Runnable> branchBuilder) {
        super(branchBuilder);
    }

    public boolean find(IntLongMap map, Predicate<GTRecipe> canHandle) {
        if (rootBranch != null) {
            searchContext.reset(this.rootBranch, map, map.toIntArray(), r -> {
                if (r.getIntContainer().match(map) && canHandle.test(r)) return r;
                return null;
            }, null);
            if (searchContext.findAny() != null) {
                return true;
            }
        }
        if (!serialRecipes.isEmpty()) {
            for (GTRecipe recipe : serialRecipes) {
                if (canHandle.test(recipe)) return true;
            }
        }
        return false;
    }

    @Override
    protected boolean supportsParallel(GTRecipe recipe) {
        return false;
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
