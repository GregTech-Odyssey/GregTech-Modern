package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.NotNull;

public class PositionYCondition extends RecipeCondition {

    public final int min;
    public final int max;

    public PositionYCondition(boolean isReverse, int min, int max) {
        super(isReverse);
        this.min = min;
        this.max = max;
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.pos_y.tooltip", this.min, this.max);
    }

    @Override
    public boolean testCondition(@NotNull GTRecipeDefinition recipe, @NotNull RecipeLogic recipeLogic) {
        int y = recipeLogic.machine.self().getPos().getY();
        return y >= this.min && y <= this.max;
    }
}
