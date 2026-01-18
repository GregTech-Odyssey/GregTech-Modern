package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.capability.IDataAccessHatch;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;

public class ResearchCondition extends RecipeCondition {

    public final String researchId;
    public final ItemStack dataStack;

    public ResearchCondition(String researchId, ItemStack dataStack) {
        this.researchId = researchId;
        this.dataStack = dataStack;
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("gtceu.recipe.research");
    }

    @Override
    public boolean testCondition(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic) {
        if (recipeLogic.machine instanceof IDataAccessHatch dataAccessHatch && dataAccessHatch.isRecipeAvailable(recipe)) {
            return true;
        } else if (recipeLogic.machine instanceof IMultiController controller) {
            for (var p : controller.getParts()) {
                if (p instanceof IDataAccessHatch dataAccessHatch && dataAccessHatch.isRecipeAvailable(recipe)) {
                    return true;
                }
            }
        }
        return false;
    }
}
