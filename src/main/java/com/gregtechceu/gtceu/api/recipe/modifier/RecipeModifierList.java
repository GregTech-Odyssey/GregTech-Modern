package com.gregtechceu.gtceu.api.recipe.modifier;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a list of RecipeModifiers that should be applied in order
 */
public final class RecipeModifierList implements RecipeModifier {

    private final RecipeModifier[] modifiers;

    public RecipeModifierList(RecipeModifier... modifiers) {
        this.modifiers = modifiers;
    }

    @Override
    public @Nullable GTRecipe applyModifier(@NotNull IRecipeHandlerHolder holder, @NotNull RecipeHandlerUnit unit, @NotNull GTRecipe recipe) {
        for (var m : modifiers) {
            recipe = m.applyModifier(holder, unit, recipe);
            if (recipe == null) {
                return null;
            }
        }
        return recipe;
    }
}
