package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ui.IRecipeInfo;

public abstract class RecipeCondition implements IRecipeInfo {

    protected final boolean isReverse;

    public RecipeCondition() {
        this(false);
    }

    public RecipeCondition(boolean isReverse) {
        this.isReverse = isReverse;
    }

    public boolean isOr() {
        return false;
    }

    public boolean check(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        boolean test = testCondition(holder, unit, recipe);
        return test != isReverse;
    }

    protected abstract boolean testCondition(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe);
}
