package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import java.util.function.BiPredicate;

public interface ICustomRecipeLogicHolder extends IRecipeHandlerHolder {

    GTRecipeDefinition createCustomRecipe(RecipeHandlerUnit unit);

    default boolean searchRecipe() {
        return false;
    }

    @Override
    default boolean findRecipe(GTRecipeType type, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> canHandle) {
        var customRecipeLogic = type.getCustomRecipeLogicRunners();
        var hasCustomRecipeLogic = !customRecipeLogic.isEmpty();
        var searchRecipe = searchRecipe();
        for (var unit : this.getInputUnits()) {
            var r = this.createCustomRecipe(unit);
            if (r != null && canHandle.test(unit, r)) return true;
            if (searchRecipe) {
                if (unit.findRecipe(type, canHandle)) return true;
                if (hasCustomRecipeLogic) {
                    for (var logic : customRecipeLogic) {
                        r = logic.createCustomRecipe(this, unit);
                        if (r != null && canHandle.test(unit, r)) return true;
                    }
                }
            }
        }
        return false;
    }
}
