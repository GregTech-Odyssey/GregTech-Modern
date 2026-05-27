package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.capability.IDataAccessHatch;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

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
    public boolean testCondition(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        if (holder instanceof IDataAccessHatch dataAccessHatch && dataAccessHatch.isRecipeAvailable(recipe)) {
            return true;
        } else if (holder instanceof IMultiController controller) {
            for (var p : controller.getParts()) {
                if (p instanceof IDataAccessHatch dataAccessHatch && dataAccessHatch.isRecipeAvailable(recipe)) {
                    return true;
                }
            }
        }
        return false;
    }
}
