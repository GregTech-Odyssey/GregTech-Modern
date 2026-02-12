package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.machine.feature.IExhaustVentMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.NotNull;

public class VentCondition extends RecipeCondition {

    public static final VentCondition INSTANCE = new VentCondition();

    private VentCondition() {}

    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.steam_vent.tooltip");
    }

    @Override
    public boolean testCondition(@NotNull GTRecipeDefinition recipe, @NotNull RecipeLogic recipeLogic) {
        if (recipeLogic.getProgress() % 10 == 0 && recipeLogic.machine instanceof IExhaustVentMachine ventMachine) {
            return !(ventMachine.isNeedsVenting() && ventMachine.isVentingBlocked());
        }
        return true;
    }
}
