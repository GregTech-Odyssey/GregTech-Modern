package com.gregtechceu.gtceu.common.machine.trait;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IWorkable;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CleanroomMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import org.jetbrains.annotations.Nullable;

public class CleanroomLogic extends RecipeLogic implements IWorkable {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(CleanroomLogic.class, RecipeLogic.MANAGED_FIELD_HOLDER);
    public static final int BASE_CLEAN_AMOUNT = 2;
    @Nullable
    private IMaintenanceMachine maintenanceMachine;
    @Nullable
    private IEnergyContainer energyContainer;
    /**
     * whether the cleanroom was active and needs an update
     */
    @Persisted
    private boolean isActiveAndNeedsUpdate;

    public CleanroomLogic(CleanroomMachine machine) {
        super(machine);
    }

    @Override
    public CleanroomMachine getMachine() {
        return (CleanroomMachine) machine;
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /**
     * Performs the actual cleaning
     * Call this method every tick in update
     */
    public void serverTick() {
        // always run this logic
        if (duration > 0) {
            if (maintenanceMachine == null || maintenanceMachine.getNumMaintenanceProblems() < 6) {
                // drain the energy
                if (!consumeEnergy()) {
                    if (progress > 0 && machine.regressWhenWaiting()) {
                        if (ConfigHolder.INSTANCE.machines.recipeProgressLowEnergy) {
                            this.progress = 1;
                        } else {
                            this.progress = Math.max(1, progress - 2);
                        }
                    }
                    // the cleanroom does not have enough energy, so it looses cleanliness
                    if (machine.self().getOffsetTimer() % duration == 0) {
                        adjustCleanAmount(true);
                    }
                    setWaiting(Component.translatable("gtceu.recipe_logic.insufficient_in").append(": ").append(EURecipeCapability.CAP.getName()));
                    return;
                }
                setStatus(Status.WORKING);
                // increase progress
                if (progress++ < getMaxProgress()) {
                    if (!machine.onWorking()) {
                        this.interruptRecipe();
                    }
                    return;
                }
                progress = 0;
                if (!machine.beforeWorking(null)) {
                    return;
                }
                adjustCleanAmount(false);
            } else {
                // has all maintenance problems
                if (progress > 0) {
                    progress--;
                }
                if (machine.self().getOffsetTimer() % duration == 0) {
                    adjustCleanAmount(true);
                }
                setStatus(Status.IDLE);
                machine.afterWorking();
            }
        }
    }

    protected void adjustCleanAmount(boolean declined) {
        // range from 5 - ~44 % per cycle instead of the 5 - 70% it was previously
        int amountToClean = BASE_CLEAN_AMOUNT + (3 * (getTierDifference() + 1));
        if (declined) amountToClean *= -1;
        // each maintenance problem lowers gain by 1
        if (maintenanceMachine != null) {
            amountToClean -= maintenanceMachine.getNumMaintenanceProblems();
        }
        getMachine().adjustCleanAmount(amountToClean);
    }

    protected boolean consumeEnergy() {
        var cleanroom = getMachine();
        // clamp to max for VA indexing
        var tier = Mth.clamp(cleanroom.getTier(), GTValues.ULV, GTValues.MAX);
        // use 3/16th an amp when fully clean otherwise 15/16th an amp during cleaning
        long energyToDrain = cleanroom.isClean() ? Math.max(8, (3 * GTValues.V[tier] / 16)) : GTValues.VA[tier];
        if (energyContainer != null) {
            long resultEnergy = energyContainer.getEnergyStored() - energyToDrain;
            if (resultEnergy >= 0L && resultEnergy <= energyContainer.getEnergyCapacity()) {
                energyContainer.removeEnergy(energyToDrain);
                return true;
            }
        }
        return false;
    }

    protected int getTierDifference() {
        int minEnergyTier = GTValues.LV;
        // clamp for ULV
        return Math.max(0, getMachine().getTier() - minEnergyTier);
    }

    public void setDuration(int max) {
        this.duration = max;
    }

    public void setMaintenanceMachine(@Nullable final IMaintenanceMachine maintenanceMachine) {
        this.maintenanceMachine = maintenanceMachine;
    }

    public void setEnergyContainer(@Nullable final IEnergyContainer energyContainer) {
        this.energyContainer = energyContainer;
    }

    /**
     * whether the cleanroom was active and needs an update
     */
    public boolean isActiveAndNeedsUpdate() {
        return this.isActiveAndNeedsUpdate;
    }

    /**
     * whether the cleanroom was active and needs an update
     */
    public void setActiveAndNeedsUpdate(final boolean isActiveAndNeedsUpdate) {
        this.isActiveAndNeedsUpdate = isActiveAndNeedsUpdate;
    }
}
