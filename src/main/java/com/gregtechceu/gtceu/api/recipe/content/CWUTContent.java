package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.codec.data.DataKeys;
import com.gregtechceu.gtceu.api.machine.feature.IComputationContainerMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.IdleReason;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;

import org.apache.commons.lang3.mutable.MutableInt;

public final class CWUTContent extends TickContent {

    public static final TickContent INSTANCE = new CWUTContent();

    private CWUTContent() {
        super("cwut");
    }

    @Override
    public void addXEIInfo(WidgetGroup group, int xOffset, GTRecipeDefinition recipe, long content, MutableInt yOffset) {
        group.addWidget(new LabelWidget(3 - xOffset, yOffset.addAndGet(10), LocalizationUtils.format("gtceu.recipe.computation_per_tick", FormattingUtil.formatNumbers(content))));
        if (recipe.data.getBoolean(DataKeys.DURATION_IS_TOTAL_CWU)) {
            group.addWidget(new LabelWidget(3 - xOffset, yOffset.addAndGet(10), LocalizationUtils.format("gtceu.recipe.total_computation", FormattingUtil.formatNumbers(recipe.duration))));
        }
    }

    @Override
    public boolean handleRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe, long contents, boolean simulated) {
        if (holder instanceof IComputationContainerMachine containerMachine && containerMachine.requestCWU(contents, simulated) >= contents) {
            return true;
        }
        IdleReason.setIdleReason(holder, IdleReason.NO_CWU);
        return false;
    }
}
