package com.gregtechceu.gtceu.api.capability.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.content.SerializerLong;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;

import org.apache.commons.lang3.mutable.MutableInt;

public class CWURecipeCapability extends RecipeCapability<Long> {

    public final static CWURecipeCapability CAP = new CWURecipeCapability();

    protected CWURecipeCapability() {
        super("cwu", 0xFFEEEE00, false, 3, SerializerLong.INSTANCE);
    }

    @Override
    public Long copyWithModifier(Long content, ContentModifier modifier) {
        return modifier.apply(content);
    }

    @Override
    public void addXEIInfo(WidgetGroup group, int xOffset, GTRecipeDefinition recipe, Long content, boolean perTick,
                           boolean isInput, MutableInt yOffset) {
        if (perTick) {
            group.addWidget(new LabelWidget(3 - xOffset, yOffset.addAndGet(10),
                    LocalizationUtils.format("gtceu.recipe.computation_per_tick", FormattingUtil.formatNumbers(content))));
        }
        if (recipe.data.getBoolean("duration_is_total_cwu")) {
            group.addWidget(new LabelWidget(3 - xOffset, yOffset.addAndGet(10),
                    LocalizationUtils.format("gtceu.recipe.total_computation", FormattingUtil.formatNumbers(recipe.duration))));
        }
    }
}
