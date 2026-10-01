package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

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
        if (isReverse) return Component.translatable("recipe.condition.pos_y.reverse.tooltip", this.min, this.max);
        return Component.translatable("recipe.condition.pos_y.tooltip", this.min, this.max);
    }

    @Override
    public @Nullable Component describeCurrent(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        return Component.translatable("gtceu.issue.current.pos_y", holder.self().getPos().getY());
    }

    @Override
    public boolean testCondition(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        int y = holder.self().getPos().getY();
        return y >= this.min && y <= this.max;
    }
}
