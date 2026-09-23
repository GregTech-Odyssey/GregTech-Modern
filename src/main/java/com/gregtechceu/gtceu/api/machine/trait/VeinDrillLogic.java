package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

/**
 * Shared recipe logic for machines that drill a bedrock vein under their own chunk.
 * <p>
 * The vein cached by subclasses is not persisted, so after a world reload it is empty while a saved recipe
 * may still be running. Every recipe search therefore goes through {@link #findAndHandleRecipe()}, which
 * re-resolves the vein before building the next recipe.
 */
public abstract class VeinDrillLogic extends RecipeLogic {

    protected VeinDrillLogic(IRecipeLogicMachine machine) {
        super(machine);
    }

    /**
     * Extra precondition checked before every search, e.g. the energy tier of the machine.
     */
    protected boolean canDrill() {
        return true;
    }

    /**
     * Resolve the cached vein if it is empty.
     *
     * @return whether a vein is available after resolving
     */
    protected abstract boolean resolveVein(ServerLevel serverLevel);

    /**
     * Build the next drilling recipe from the resolved vein.
     */
    @Nullable
    protected abstract GTRecipe buildDrillRecipe();

    /**
     * Called after the outputs of a finished recipe are handled, e.g. to deplete the vein.
     */
    protected void onDrillFinish() {}

    @Override
    public boolean findAndHandleRecipe() {
        if (!(getMachine().getLevel() instanceof ServerLevel serverLevel) || !canDrill()) return false;
        lastRecipe = null;
        if (!resolveVein(serverLevel)) return false;
        var match = buildDrillRecipe();
        return match != null && machine.matchTickRecipe(match) && machine.matchRecipeOutput(match) && setupRecipe(RecipeHandlerUnit.NO_DATA, match);
    }

    @Override
    public boolean onRecipeFinish() {
        machine.afterWorking();
        if (lastRecipe != null) machine.handleRecipeOutput(lastRecipe);
        onDrillFinish();
        if (suspendAfterFinish) {
            setStatus(SUSPEND);
            suspendAfterFinish = false;
        } else {
            if (findAndHandleRecipe()) return true;
            setStatus(IDLE);
        }
        return false;
    }

    protected int getChunkX() {
        return SectionPos.blockToSectionCoord(getMachine().getPos().getX());
    }

    protected int getChunkZ() {
        return SectionPos.blockToSectionCoord(getMachine().getPos().getZ());
    }
}
