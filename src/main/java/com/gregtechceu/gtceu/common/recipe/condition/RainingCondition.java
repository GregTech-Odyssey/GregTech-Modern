package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ui.RecipeInfoBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public class RainingCondition extends RecipeCondition {

    private static final Component LABEL = Component.translatable("gtceu.recipe.info.rain");

    public final float level;

    public RainingCondition(boolean isReverse, float level) {
        super(isReverse);
        this.level = level;
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.rain.tooltip", level);
    }

    @Override
    public void appendInfo(GTRecipeDefinition recipe, RecipeInfoBuilder info) {
        info.requirement(LABEL, () -> Component.translatable("gtceu.recipe.info.at_least", level));
    }

    @Override
    public boolean testCondition(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        Level level = holder.self().getLevel();
        return level != null && level.getRainLevel(1) >= this.level;
    }
}
