package com.gregtechceu.gtceu.api.recipe.modifier;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a list of RecipeModifiers that should be applied in order
 *
 * <p>
 * 按数组顺序依次施加；任意一个返回 {@code null} 就立即中断并返回 {@code null}，
 * 因此它自身也是一个可直接使用的 {@link RecipeModifier}。
 */
public final class RecipeModifierList implements RecipeModifier {

    private final RecipeModifier[] modifiers;

    public RecipeModifierList(RecipeModifier... modifiers) {
        this.modifiers = modifiers;
    }

    /**
     * 依次施加每个修饰器。
     *
     * @return 最后一个修饰器的结果；中途任何一个返回 {@code null} 就返回 {@code null}
     */
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
