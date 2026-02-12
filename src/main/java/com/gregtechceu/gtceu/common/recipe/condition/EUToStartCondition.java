package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;

import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.NotNull;

public class EUToStartCondition extends RecipeCondition {

    public final long euToStart;

    public EUToStartCondition(boolean isReverse, long euToStart) {
        super(isReverse);
        this.euToStart = euToStart;
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.eu_to_start.tooltip");
    }

    @Override
    public boolean testCondition(@NotNull GTRecipeDefinition recipe, @NotNull RecipeLogic recipeLogic) {
        return recipeLogic.getMachine().getTraits().stream().filter(IEnergyContainer.class::isInstance).anyMatch(energyContainer -> ((IEnergyContainer) energyContainer).getEnergyCapacity() > euToStart);
    }
}
