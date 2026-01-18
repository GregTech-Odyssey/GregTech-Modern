package com.gregtechceu.gtceu.api.recipe.research;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.recipe.condition.ResearchCondition;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.ResearchManager;

import net.minecraft.world.item.Item;

import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.SCANNER_RECIPES;

public final class ScannerBuilder extends ResearchBuilder<ScannerBuilder> {

    static final int DEFAULT_SCANNER_DURATION = 1200;
    static final int DEFAULT_SCANNER_EUT = GTValues.VA[GTValues.HV];

    @Override
    protected Item getDefaultDataItem() {
        return GTItems.TOOL_DATA_STICK.get();
    }

    @Override
    public ResearchCondition build(GTRecipeType recipeType) {
        validateResearchItem();
        if (duration <= 0) duration = DEFAULT_SCANNER_DURATION;
        if (eut <= 0) eut = DEFAULT_SCANNER_EUT;

        var dataStack = dataItem.getDefaultInstance();
        ResearchManager.writeResearchToNBT(dataStack.getOrCreateTag(), researchId, recipeType);

        SCANNER_RECIPES.recipeBuilder(FormattingUtil.toLowerCaseUnderscore(researchId))
                .inputItems(dataItem)
                .inputItems(researchStack)
                .outputItems(dataStack)
                .duration(duration)
                .EUt(eut)
                .researchScan(true)
                .save();

        return new ResearchCondition(researchId, dataStack);
    }
}
