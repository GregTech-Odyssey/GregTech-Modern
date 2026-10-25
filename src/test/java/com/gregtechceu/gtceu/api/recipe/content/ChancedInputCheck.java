package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.GTRecipeBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

/** Regression check for the consumption/output direction of voltage-tier bonuses. */
public final class ChancedInputCheck {

    private ChancedInputCheck() {}

    public static void main(String[] args) {
        var id = new ResourceLocation("gtceu", "chanced_input_check");
        var item = new TestIngredient(true);
        var fluid = new TestIngredient(false);
        var builder = new GTRecipeBuilder(id).chance(200).tierChanceBoost(10)
                .inputItems(item, 1L).inputFluids(fluid, 1000L)
                .outputItems(item, 1L).outputFluids(fluid, 1000L);
        var items = builder.getItemInputs().build();
        var fluids = builder.getFluidInputs().build();
        var itemOutputs = builder.getItemOutputs().build();
        var fluidOutputs = builder.getFluidOutputs().build();
        var rng = RandomSource.create(42);
        for (int tier = GTValues.LV; tier <= GTValues.LV + 25; tier++) {
            int inputChance = Math.max(0, 200 - 10 * (tier - GTValues.LV));
            int outputChance = 200 + 10 * (tier - GTValues.LV);
            check(items, GTValues.LV, tier, inputChance, rng);
            check(fluids, GTValues.LV, tier, inputChance, rng);
            check(itemOutputs, GTValues.LV, tier, outputChance, rng);
            check(fluidOutputs, GTValues.LV, tier, outputChance, rng);
        }
        check(items, GTValues.ULV, GTValues.LV, 200, rng);
        check(items, GTValues.ULV, GTValues.MV, 190, rng);
        check(fluids, GTValues.ULV, GTValues.LV, 200, rng);
        check(fluids, GTValues.ULV, GTValues.MV, 190, rng);
        check(items, GTValues.MV, GTValues.LV, 200, rng);
        var fixed = new GTRecipeBuilder(id).chance(200).inputItems(item, 1L).getItemInputs().build();
        check(fixed, GTValues.LV, GTValues.UHV, 200, rng);
        var penalty = new GTRecipeBuilder(id).chance(9900).tierChanceBoost(-100)
                .inputItems(item, 1L).getItemInputs().build();
        check(penalty, GTValues.LV, GTValues.HV, 10000, rng);
        int unchanged = ChanceBoostFunction.NONE.getBoostedChance(items.chance(0), items.boost(0), GTValues.LV, GTValues.UHV);
        if (unchanged != 200) throw new AssertionError("Disabled bonuses changed consumption chance");
        System.out.println("ChancedInputCheck PASS");
    }

    private static void check(ContentList contents, int recipeTier, int machineTier, int expectedChance, RandomSource rng) {
        int chance = ChanceBoostFunction.OVERCLOCK.getBoostedChance(contents.chance(0), contents.boost(0), recipeTier, machineTier);
        if (chance != expectedChance) throw new AssertionError("Tier " + machineTier + ": expected " + expectedChance + ", got " + chance);
        // A full probability denominator of rolls gives an exact, seed-independent consumption count.
        long amount = contents.amount(0);
        long consumed = ContentRoll.roll(contents.effective(0, ContentList.MAX_CHANCE), contents.rollUnit(0), chance, rng);
        if (consumed != amount * expectedChance) throw new AssertionError("Incorrect consumption: " + consumed);
    }

    // Registration and probability calculation do not need a bootstrapped item/fluid registry.
    private record TestIngredient(boolean isItem) implements KeyIngredient {

        @Override
        public byte kind() {
            return TAG;
        }

        @Override
        public AEKeyType getType() {
            return null;
        }

        @Override
        public AEKey key() {
            return null;
        }

        @Override
        public boolean test(AEKey key) {
            return false;
        }

        @Override
        public boolean isFluid() {
            return !isItem;
        }
    }
}
