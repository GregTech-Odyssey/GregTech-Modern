package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.util.Mth;

@FunctionalInterface
public interface ChanceBoostFunction {

    /**
     * Chance boosting function based on the number of performed overclocks
     */
    ChanceBoostFunction OVERCLOCK = (chance, boost, recipeTier, chanceTier) -> {
        int tierDiff = chanceTier - recipeTier;
        if (tierDiff <= 0) return chance; // equal or invalid tiers do not boost at all
        if (recipeTier == GTValues.ULV) tierDiff--; // LV does not boost over ULV
        return Mth.clamp(chance + (boost * tierDiff), 0, ContentList.MAX_CHANCE);
    };

    /**
     * Chance boosting function which performs no boosting
     */
    ChanceBoostFunction NONE = (chance, boost, recipeTier, chanceTier) -> chance;

    int getBoostedChance(int chance, int boost, int recipeTier, int chanceTier);
}
