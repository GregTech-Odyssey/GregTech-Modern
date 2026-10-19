package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.worldgen.bedrock.BedrockVeinDefinition;
import com.gregtechceu.gtceu.api.data.worldgen.bedrock.BedrockVeinEntry;
import com.gregtechceu.gtceu.api.data.worldgen.bedrock.BedrockVeinKind;
import com.gregtechceu.gtceu.api.data.worldgen.bedrock.BedrockVeinSavedData;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IVeinDrillMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeIOList;

import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Drills the single bedrock vein of the controller's chunk: one operation every {@link #MAX_PROGRESS} ticks,
 * producing the vein's per-operation yield times the rig multiplier, 50% more when overclocked.
 */
public abstract class BedrockVeinDrillLogic<D extends BedrockVeinDefinition> extends VeinDrillLogic {

    public static final int MAX_PROGRESS = 20;
    private final BedrockVeinKind<D> kind;
    @Nullable
    private BedrockVeinSavedData<D> data;
    @Nullable
    private D vein;
    @Nullable
    private D outputsVein;
    private int outputsProduced = -1;
    private RecipeIOList.Snapshot outputs = RecipeIOList.Snapshot.EMPTY;

    protected BedrockVeinDrillLogic(IVeinDrillMachine machine, BedrockVeinKind<D> kind) {
        super(machine);
        this.kind = kind;
    }

    protected abstract GTRecipe buildDrillRecipe(D vein, int amount);

    protected abstract void addExpectedOutputs(List<RecipeIOList.Entry> outputs, D vein, int produced);

    @Nullable
    public D getVein() {
        return vein;
    }

    public int getOperationsRemaining() {
        return vein == null || data == null ? 0 : entry().operationsRemaining();
    }

    public RecipeIOList.Snapshot getExpectedOutputs() {
        if (vein == null || data == null) return RecipeIOList.Snapshot.EMPTY;
        int produced = produced(entry());
        if (vein != outputsVein || produced != outputsProduced) {
            outputsVein = vein;
            outputsProduced = produced;
            var list = new ArrayList<RecipeIOList.Entry>();
            addExpectedOutputs(list, vein, produced);
            outputs = new RecipeIOList.Snapshot(MAX_PROGRESS, list);
        }
        return outputs;
    }

    public long producedPerSecond() {
        return vein == null || data == null ? 0 : (long) produced(entry()) * 20 / MAX_PROGRESS;
    }

    protected IVeinDrillMachine drill() {
        return (IVeinDrillMachine) machine;
    }

    protected int rigMultiplier() {
        return kind.rigMultiplier(drill().getDrillTier());
    }

    protected boolean isOverclocked() {
        return drill().getEnergyTier() > drill().getDrillTier();
    }

    protected int produced(BedrockVeinEntry<D> entry) {
        int produced = entry.yieldPerOperation() * rigMultiplier();
        return isOverclocked() ? produced * 3 / 2 : produced;
    }

    protected long drillEUt() {
        return GTValues.VA[drill().getEnergyTier()];
    }

    @Override
    protected boolean canDrill() {
        return drill().getEnergyTier() >= drill().getDrillTier();
    }

    @Override
    protected boolean resolveVein(ServerLevel serverLevel) {
        if (vein == null) {
            if (data == null) data = kind.data(serverLevel);
            vein = data.vein(getChunkX(), getChunkZ());
        }
        return vein != null;
    }

    @Nullable
    @Override
    protected GTRecipe buildDrillRecipe() {
        return vein == null || data == null ? null : buildDrillRecipe(vein, produced(entry()));
    }

    @Override
    protected void onDrillFinish() {
        if (data == null) return;
        int chance = kind.depletionChance(drill().getDrillTier());
        if (chance == 1 || GTValues.RNG.nextInt(chance) == 0) data.deplete(getChunkX(), getChunkZ());
    }

    @SuppressWarnings("DataFlowIssue")
    private BedrockVeinEntry<D> entry() {
        return data.entry(getChunkX(), getChunkZ());
    }
}
