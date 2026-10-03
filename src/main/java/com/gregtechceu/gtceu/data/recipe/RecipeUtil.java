package com.gregtechceu.gtceu.data.recipe;

public class RecipeUtil {

    public static int getRatioForDistillery(long fluidInput, long fluidOutput,
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

    public static boolean isFluidStackDivisibleForDistillery(long amount, int divisor) {
        return amount % divisor == 0 && amount / divisor >= 25;
    }
}
