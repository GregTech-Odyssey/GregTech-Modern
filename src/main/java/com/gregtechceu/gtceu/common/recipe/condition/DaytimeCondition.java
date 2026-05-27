package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

public class DaytimeCondition extends RecipeCondition {

    public static final DaytimeCondition DAY = new DaytimeCondition(false);
    public static final DaytimeCondition NIGHT = new DaytimeCondition(true);

    private DaytimeCondition(boolean isNight) {
        super(isNight);
    }

    @Override
    public Component getTooltips() {
        if (isReverse) {
            return Component.translatable("recipe.condition.daytime.night.tooltip");
        } else {
            return Component.translatable("recipe.condition.daytime.day.tooltip");
        }
    }

    @Override
    public boolean testCondition(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        Level level = holder.self().getLevel();
        return level != null && !level.isNight();
    }
}
