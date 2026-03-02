package com.gregtechceu.gtceu.api.recipe.content;

import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.machine.feature.IElectricMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.IdleReason;

public final class EUTContent extends TickContent {

    public static final TickContent INSTANCE = new EUTContent();

    private EUTContent() {
        super("eut");
    }

    @Override
    public boolean handleRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe, long contents, boolean simulated) {
        if (holder instanceof IElectricMachine electricMachine) {
            if (contents > 0) {
                if (electricMachine.useEnergy(contents, simulated)) {
                    return true;
                } else {
                    IdleReason.setIdleReason(holder, IdleReason.NO_EU);
                    return false;
                }
            } else {
                if (electricMachine.generateEnergy(contents, simulated)) {
                    return true;
                } else {
                    IdleReason.setIdleReason(holder, IdleReason.INSUFFICIENT_OUT);
                    return false;
                }
            }
        }
        return false;
    }
}
