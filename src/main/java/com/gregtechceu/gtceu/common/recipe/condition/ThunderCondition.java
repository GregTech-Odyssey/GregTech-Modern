package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;

public class ThunderCondition extends RecipeCondition {

    public final float level;

    public ThunderCondition(boolean isReverse, float level) {
        super(isReverse);
        this.level = level;
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.thunder.tooltip", level);
    }

    @Override
    public boolean testCondition(@NotNull GTRecipeDefinition recipe, @NotNull RecipeLogic recipeLogic) {
        Level level = recipeLogic.machine.self().getLevel();
        return level != null && level.getThunderLevel(1) >= this.level;
    }
}
