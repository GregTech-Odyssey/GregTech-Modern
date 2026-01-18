package com.gregtechceu.gtceu.api.recipe.research;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.recipe.condition.ResearchCondition;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.ResearchManager;

import net.minecraft.world.item.Item;

import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.RESEARCH_STATION_RECIPES;

public final class StationBuilder extends ResearchBuilder<StationBuilder> {

    static final int DEFAULT_STATION_EUT = GTValues.VA[GTValues.LuV];
    static final int DEFAULT_STATION_TOTAL_CWUT = 4000;
    private int cwut;
    private int totalCWU;

    public StationBuilder CWUt(int cwut) {
        this.cwut = cwut;
        this.totalCWU = cwut * DEFAULT_STATION_TOTAL_CWUT;
        return this;
    }

    public StationBuilder CWUt(int cwut, int totalCWU) {
        this.cwut = cwut;
        this.totalCWU = totalCWU;
        return this;
    }

    @Override
    protected Item getDefaultDataItem() {
        return ResearchManager.DATA_ITEM_PROVIDER.apply(cwut);
    }

    @Override
    public ResearchCondition build(GTRecipeType recipeType) {
        validateResearchItem();
        if (cwut <= 0 || totalCWU <= 0) {
            throw new IllegalArgumentException("CWU/t and total CWU must both be set, and non-zero!");
        }
        if (cwut > totalCWU) {
            throw new IllegalArgumentException("Total CWU cannot be greater than CWU/t!");
        }
        int duration = totalCWU;
        if (eut <= 0) eut = DEFAULT_STATION_EUT;

        var dataStack = dataItem.getDefaultInstance();
        ResearchManager.writeResearchToNBT(dataStack.getOrCreateTag(), researchId, recipeType);

        RESEARCH_STATION_RECIPES.recipeBuilder(FormattingUtil.toLowerCaseUnderscore(researchId))
                .inputItems(dataItem)
                .inputItems(researchStack)
                .outputItems(dataStack)
                .EUt(eut)
                .CWUt(cwut)
                .totalCWU(duration)
                .save();

        return new ResearchCondition(researchId, dataStack);
    }
}
