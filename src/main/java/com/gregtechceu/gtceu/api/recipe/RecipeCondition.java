package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import net.minecraft.network.chat.Component;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public abstract class RecipeCondition {

    protected boolean isReverse;

    public RecipeCondition() {
        this(false);
    }

    public RecipeCondition(boolean isReverse) {
        this.isReverse = isReverse;
    }

    public boolean isOr() {
        return false;
    }

    public abstract Component getTooltips();

    public boolean check(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic) {
        boolean test = testCondition(recipe, recipeLogic);
        return test != isReverse;
    }

    protected abstract boolean testCondition(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic);

    /**
     * @return {@code this}.
     */
    public RecipeCondition setReverse(final boolean isReverse) {
        this.isReverse = isReverse;
        return this;
    }
}
