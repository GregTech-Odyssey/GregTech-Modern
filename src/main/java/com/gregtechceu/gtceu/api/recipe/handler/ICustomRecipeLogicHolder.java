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
        var inputs = this.getInputUnits();
        if (inputs.isEmpty()) {
            var r = this.createCustomRecipe(RecipeHandlerUnit.NO_DATA);
            return r != null && canHandle.test(RecipeHandlerUnit.NO_DATA, r);
        } else {
            var customRecipeLogic = type.getCustomRecipeLogicRunners();
            var hasCustomRecipeLogic = !customRecipeLogic.isEmpty();
            var searchRecipe = searchRecipe();
            for (var unit : inputs) {
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
}
