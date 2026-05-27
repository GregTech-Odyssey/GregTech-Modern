package com.gregtechceu.gtceu.api.recipe.expand;

import com.gregtechceu.gtceu.api.machine.feature.IComputationContainerMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandlerHolder;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.common.data.GTRecipeDataKeys;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.LocalizationUtils;

import com.fast.recipesearch.IntLongMap;
import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.datasream.DataComponentKey;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CWUTExpander extends DataComponentKey<Long> implements ContentExpander {

    public static final CWUTExpander INSTANCE = new CWUTExpander();

    private CWUTExpander() {
        super("cwut", CombinationCodec.LONG_CODEC);
    }

    @Override
    public boolean isTick() {
        return true;
    }

    @Override
    public boolean handle(IO io, @NotNull IRecipeHandlerHolder holder, @Nullable RecipeHandlerUnit unit, @NotNull GTRecipe recipe, boolean simulate) {
        var cwu = recipe.getInputCWUt();
        if (cwu < 1) return true;
        if (holder instanceof IComputationContainerMachine machine) {
            return machine.requestCWU(cwu, simulate) >= cwu;
        } else {
            return false;
        }
    }

    @Override
    public void extractInput(GTRecipeDefinition recipe, IntLongMap map) {}

    @Override
    public long getParallel(IRecipeHandlerHolder holder, RecipeHandlerUnit unit, GTRecipe recipe, long parallel) {
        var cwu = recipe.getInputCWUt();
        if (cwu < 1) return parallel;
        if (holder instanceof IComputationContainerMachine machine) {
            return Math.min(parallel, machine.getMaxCWU() / cwu);
        } else {
            return 0;
        }
    }

    @Override
    public void setParallel(GTRecipe recipe, long parallel) {
        var cwu = recipe.getInputCWUt();
        if (cwu < 1) return;
        recipe.setCWUt(cwu * parallel);
    }

    @Override
    public void addXEIInfo(GTRecipeDefinition recipe, WidgetGroup group, int xOffset, MutableInt yOffset) {
        group.addWidget(new LabelWidget(3 - xOffset, yOffset.addAndGet(10),
                LocalizationUtils.format("gtceu.recipe.computation_per_tick", FormattingUtil.formatNumbers(recipe.data.getLong(GTRecipeDataKeys.CWUT)))));
        if (recipe.data.getBoolean(GTRecipeDataKeys.DURATION_IS_TOTAL_CWU)) {
            group.addWidget(new LabelWidget(3 - xOffset, yOffset.addAndGet(10),
                    LocalizationUtils.format("gtceu.recipe.total_computation", FormattingUtil.formatNumbers(recipe.duration))));
        }
    }
}
