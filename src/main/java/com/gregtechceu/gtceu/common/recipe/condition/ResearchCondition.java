package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.capability.IDataAccessHatch;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ui.RecipeInfoBuilder;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeDisplaySlots;

import com.lowdragmc.lowdraglib.jei.IngredientIO;

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

    /** 配方页：一句"需要研究"，并把所需的研究数据作为催化剂展示槽（开启研究系统时）。 */
    @Override
    public void appendInfo(GTRecipeDefinition recipe, RecipeInfoBuilder info) {
        info.sentence(this::getTooltips);
        if (ConfigHolder.INSTANCE.machines.enableResearch) {
            info.slot(() -> RecipeDisplaySlots.item(dataStack, IngredientIO.CATALYST));
        }
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
