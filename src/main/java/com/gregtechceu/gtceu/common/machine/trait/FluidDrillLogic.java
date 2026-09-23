package com.gregtechceu.gtceu.common.machine.trait;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.FluidVeinWorldEntry;
import com.gregtechceu.gtceu.api.machine.trait.VeinDrillLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeBuilder;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FluidDrillMachine;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

public class FluidDrillLogic extends VeinDrillLogic {

    public static final int MAX_PROGRESS = 20;
    @Nullable
    private Fluid veinFluid;

    public FluidDrillLogic(FluidDrillMachine machine) {
        super(machine);
    }

    @Override
    public FluidDrillMachine getMachine() {
        return (FluidDrillMachine) super.getMachine();
    }

    @Override
    protected boolean canDrill() {
        return getMachine().getEnergyTier() >= getMachine().getTier();
    }

    @Override
    protected boolean resolveVein(ServerLevel serverLevel) {
        if (veinFluid == null) {
            veinFluid = BedrockFluidVeinSavedData.getOrCreate(serverLevel).getFluidInChunk(getChunkX(), getChunkZ());
        }
        return veinFluid != null;
    }

    @Nullable
    @Override
    protected GTRecipe buildDrillRecipe() {
        if (getMachine().getLevel() instanceof ServerLevel serverLevel && veinFluid != null) {
            var data = BedrockFluidVeinSavedData.getOrCreate(serverLevel);
            return GTRecipeBuilder.ofRaw().duration(MAX_PROGRESS).EUt(GTValues.VA[getMachine().getEnergyTier()]).outputFluids(new FluidStack(veinFluid, getFluidToProduce(data.getFluidVeinWorldEntry(getChunkX(), getChunkZ())))).buildRawRecipe();
        }
        return null;
    }

    public int getFluidToProduce() {
        if (getMachine().getLevel() instanceof ServerLevel serverLevel && veinFluid != null) {
            var data = BedrockFluidVeinSavedData.getOrCreate(serverLevel);
            return getFluidToProduce(data.getFluidVeinWorldEntry(getChunkX(), getChunkZ()));
        }
        return 0;
    }

    private int getFluidToProduce(FluidVeinWorldEntry entry) {
        var definition = entry.getDefinition();
        if (definition != null) {
            int depletedYield = definition.getDepletedYield();
            int regularYield = entry.getFluidYield();
            int remainingOperations = entry.getOperationsRemaining();
            int produced = Math.max(depletedYield, regularYield * remainingOperations / BedrockFluidVeinSavedData.MAXIMUM_VEIN_OPERATIONS);
            produced *= FluidDrillMachine.getRigMultiplier(getMachine().getTier());
            // Overclocks produce 50% more fluid
            if (isOverclocked()) {
                produced = produced * 3 / 2;
            }
            return produced;
        }
        return 0;
    }

    @Override
    protected void onDrillFinish() {
        depleteVein();
    }

    protected void depleteVein() {
        if (getMachine().getLevel() instanceof ServerLevel serverLevel) {
            int chance = FluidDrillMachine.getDepletionChance(getMachine().getTier());
            var data = BedrockFluidVeinSavedData.getOrCreate(serverLevel);
            // chance to deplete based on the rig
            if (chance == 1 || GTValues.RNG.nextInt(chance) == 0) {
                data.depleteVein(getChunkX(), getChunkZ(), 0, false);
            }
        }
    }

    protected boolean isOverclocked() {
        return getMachine().getEnergyTier() > getMachine().getTier();
    }

    @Nullable
    public Fluid getVeinFluid() {
        return this.veinFluid;
    }
}
