package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import net.minecraft.util.RandomSource;

public final class ContentRoll {

    public static final RandomSource RNG = RandomSource.create();

    private ContentRoll() {}

    public static long roll(long effective, long unit, int boosted, RandomSource rng) {
        if (effective <= 0 || boosted <= 0) return 0;
        if (boosted >= ContentList.MAX_CHANCE) return effective;
        long q = effective / unit;
        long r = effective - q * unit;
        long frac = r == 0 ? 0 : (long) ((double) r * boosted / unit);
        long hits = (q / ContentList.MAX_CHANCE) * boosted + ((q % ContentList.MAX_CHANCE) * boosted + frac + rng.nextInt(ContentList.MAX_CHANCE)) / ContentList.MAX_CHANCE;
        return Keys.multiply(hits, unit);
    }

    public static long rolled(GTRecipe recipe, ContentList list, int i, RandomSource rng) {
        int chance = list.chances[i];
        if (chance == 0) return 0;
        long effective = list.effective(i, recipe.scale);
        if (chance == ContentList.MAX_CHANCE) return effective;
        var definition = recipe.definition;
        int boosted = definition.chanceFunction.getBoostedChance(chance, list.boosts[i], definition.tier, definition.tier + recipe.ocLevel);
        return roll(effective, list.rollUnits[i], boosted, rng);
    }
}
