package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import org.jetbrains.annotations.Nullable;

public interface IWorkableMultiController extends IMultiController, IRecipeLogicMachine {

    /**
     * The instance of {@link IParallelHatch} attached to this Controller.
     * <p>
     * Note that this will return a singular instance, and will not account for multiple attached IParallelHatches
     */
    @Nullable
    IParallelHatch getParallelHatch();

    /**
     *
     * @return Whether batching is enabled on this multiblock
     */
    default boolean isBatchEnabled() {
        return false;
    }

    default boolean hasBatchConfig() {
        return true;
    }

    @Override
    default void notifyStatusChanged(int oldStatus, int newStatus) {
        var self = self();
        if (self.isRemote()) return;
        self.requestSync();
        if (newStatus == RecipeLogic.WORKING) {
            self.updateActiveBlock(true);
        } else if (oldStatus == RecipeLogic.WORKING) {
            self.updateActiveBlock(false);
        }
    }

    IWorkableMultiPart[] getOnWorkingPart();

    IWorkableMultiPart[] getBeforeWorkingPart();

    IWorkableMultiPart[] getAfterWorkingPart();

    IWorkableMultiPart[] getModifyRecipePart();
}
