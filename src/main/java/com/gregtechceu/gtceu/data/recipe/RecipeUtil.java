package com.gregtechceu.gtceu.data.recipe;

import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;

public class RecipeUtil {

    public static int getRatioForDistillery(FluidIngredient fluidInput, FluidIngredient fluidOutput,
                                            int count) {
        int[] divisors = new int[] { 2, 5, 10, 25, 50 };
        int ratio = -1;

        for (int divisor : divisors) {

            if (!isFluidStackDivisibleForDistillery(fluidInput, divisor))
                continue;

            if (!isFluidStackDivisibleForDistillery(fluidOutput, divisor))
                continue;

            if (count % divisor != 0)
                continue;

            ratio = divisor;
        }

        return Math.max(1, ratio);
    }

    public static boolean isFluidStackDivisibleForDistillery(FluidIngredient fluidStack, int divisor) {
        return fluidStack.getAmount() % divisor == 0 && fluidStack.getAmount() / divisor >= 25;
    }
}
