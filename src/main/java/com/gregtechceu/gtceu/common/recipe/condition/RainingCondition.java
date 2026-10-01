package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

public class RainingCondition extends RecipeCondition {

    public final float level;

    public RainingCondition(boolean isReverse, float level) {
        super(isReverse);
        this.level = level;
    }

    @Override
    public Component getTooltips() {
        if (isReverse) return Component.translatable("recipe.condition.rain.reverse.tooltip", level);
        return Component.translatable("recipe.condition.rain.tooltip", level);
    }

    @Override
    public @Nullable Component describeCurrent(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        Level level = holder.self().getLevel();
        if (level == null) return null;
        return Component.translatable("gtceu.issue.current.rain", String.format("%.2f", level.getRainLevel(1)));
    }

    @Override
    public boolean testCondition(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        Level level = holder.self().getLevel();
        return level != null && level.getRainLevel(1) >= this.level;
    }
}
