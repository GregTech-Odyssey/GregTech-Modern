package com.gregtechceu.gtceu.common.machine.trait.customlogic;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyTypes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

public enum CannerLogic implements GTRecipeType.ICustomRecipeLogic {

    INSTANCE;

    @SuppressWarnings("ConstantValue")
    @Override
    public @Nullable GTRecipeDefinition createCustomRecipe(IRecipeHandlerHolder holder, RecipeHandlerUnit unit) {
        List<AEItemKey> itemKeys = new ArrayList<>();
        List<FluidStack> fluidStacks = new ArrayList<>();
        if (!collect(unit, itemKeys, fluidStacks)) return null;

        for (var itemKey : itemKeys) {
            var single = Keys.displayStack(itemKey);
            var copy = itemKey.toStack(1);
            var fluidHandler = FluidUtil.getFluidHandler(copy).orElse(null);
            if (fluidHandler == null) continue;
            // Try to drain first
            var fluid = fluidHandler.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
            if (!fluid.isEmpty()) {
                return GTRecipeTypes.CANNER_RECIPES
                        .recipeBuilder("drain_fluid")
                        .inputItems(single)
                        .outputItems(fluidHandler.getContainer())
                        .outputFluids(fluid)
                        .duration(Math.max(16, fluid.getAmount() / 64))
                        .EUt(4)
                        .build();
            }

            for (var fluidStack : fluidStacks) {
                var fluidCopy = fluidStack.copy();
                var filled = fluidHandler.fill(fluidCopy, FluidAction.EXECUTE);
                if (filled == 0) continue;
                fluidCopy.setAmount(filled);
                return GTRecipeTypes.CANNER_RECIPES
                        .recipeBuilder("fill_fluid")
                        .inputItems(single)
                        .inputFluids(fluidCopy)
                        .outputItems(fluidHandler.getContainer())
                        .duration(Math.max(16, filled / 64))
                        .EUt(4)
                        .build();
            }
        }
        return null;
    }

    private static boolean collect(RecipeHandlerUnit rhl, List<AEItemKey> itemKeys, List<FluidStack> fluidStacks) {
        rhl.forEachKey(AEKeyTypes.ITEMS, true, (key, amount) -> {
            if (key instanceof AEItemKey itemKey && amount > 0) itemKeys.add(itemKey);
            return false;
        });
        if (itemKeys.isEmpty()) return false;
        rhl.forEachKey(AEKeyTypes.FLUIDS, true, (key, amount) -> {
            if (key instanceof AEFluidKey fluidKey && amount > 0) fluidStacks.add(Keys.toFluidStack(fluidKey, amount));
            return false;
        });
        return !fluidStacks.isEmpty();
    }
}
