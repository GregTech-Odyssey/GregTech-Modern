package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ui.RecipeInfoBuilder;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeDisplaySlots;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.jei.IngredientIO;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

public class AdjacentFluidCondition extends RecipeCondition {

    public final Fluid A;
    public final Fluid B;

    public AdjacentFluidCondition(boolean isReverse, Fluid a, Fluid b) {
        super(isReverse);
        A = a;
        B = b;
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("recipe.condition.rock_breaker.tooltip");
    }

    /** 配方页：一句说明，并把两侧需要的流体作为展示槽。 */
    @Override
    public void appendInfo(GTRecipeDefinition recipe, RecipeInfoBuilder info) {
        info.sentence(this::getTooltips);
        if (A != Fluids.EMPTY) info.slot(() -> RecipeDisplaySlots.fluid(new FluidStack(A, 1000), IngredientIO.CATALYST));
        if (B != Fluids.EMPTY) info.slot(() -> RecipeDisplaySlots.fluid(new FluidStack(B, 1000), IngredientIO.CATALYST));
    }

    @Override
    public boolean testCondition(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipeDefinition recipe) {
        boolean hasFluidA = false, hasFluidB = false;
        if (holder instanceof IMultiController) {
            var as = unit.getFluidAmount(false, A, B);
            if (as[0] > 0) hasFluidA = true;
            if (as[1] > 0) hasFluidB = true;
        } else {
            for (Direction side : GTUtil.DIRECTIONS) {
                if (side.getAxis() != Direction.Axis.Y) {
                    var fluid = holder.self().getNeighborFluidState(side).getType();
                    if (fluid == A) {
                        hasFluidA = true;
                    } else if (fluid == B) {
                        hasFluidB = true;
                    }
                    if (hasFluidA && hasFluidB) return true;
                }
            }
        }
        return hasFluidA && hasFluidB;
    }
}
