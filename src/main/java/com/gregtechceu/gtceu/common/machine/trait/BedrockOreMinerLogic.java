package com.gregtechceu.gtceu.common.machine.trait;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreVeinSavedData;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.OreVeinWorldEntry;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.WeightedMaterial;
import com.gregtechceu.gtceu.api.machine.trait.VeinDrillLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeBuilder;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.BedrockOreMinerMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BedrockOreMinerLogic extends VeinDrillLogic {

    public static final int MAX_PROGRESS = 20;
    @Nullable
    private List<WeightedMaterial> veinMaterials;

    public BedrockOreMinerLogic(BedrockOreMinerMachine machine) {
        super(machine);
    }

    @Override
    public BedrockOreMinerMachine getMachine() {
        return (BedrockOreMinerMachine) super.getMachine();
    }

    @Override
    protected boolean canDrill() {
        return getMachine().getEnergyTier() >= getMachine().getTier();
    }

    @Override
    protected boolean resolveVein(ServerLevel serverLevel) {
        if (veinMaterials == null) {
            veinMaterials = BedrockOreVeinSavedData.getOrCreate(serverLevel).getOreInChunk(getChunkX(), getChunkZ());
        }
        return veinMaterials != null;
    }

    @Nullable
    @Override
    protected GTRecipe buildDrillRecipe() {
        if (getMachine().getLevel() instanceof ServerLevel serverLevel && veinMaterials != null) {
            WeightedMaterial wm = GTUtil.getRandomItem(serverLevel.random, veinMaterials);
            if (wm == null) return null;
            Material material = wm.material();
            ItemStack stack = ChemicalHelper.get(TagPrefix.get(ConfigHolder.INSTANCE.machines.bedrockOreDropTagPrefix), material, getOreToProduce());
            if (stack.isEmpty()) stack = ChemicalHelper.get(TagPrefix.crushed, material, getOreToProduce()); // backup
            // 1:
            // crushed;
            // if raw
            // ore
            // doesn't
            // exist
            if (stack.isEmpty()) stack = ChemicalHelper.get(TagPrefix.gem, material, getOreToProduce()); // backup 2:
            // gem; if
            // crushed ore
            // doesn't
            // exist
            if (stack.isEmpty()) stack = ChemicalHelper.get(TagPrefix.ore, material, getOreToProduce()); // backup 3:
            // normal ore;
            // if gem
            // doesn't
            // exist.
            if (stack.isEmpty()) stack = ChemicalHelper.get(TagPrefix.dust, material, getOreToProduce()); // backup 4:
            // fallback to
            // dust
            if (stack.isEmpty()) {
                return null;
            }
            return GTRecipeBuilder.ofRaw().duration(MAX_PROGRESS).EUt(GTValues.VA[getMachine().getEnergyTier()]).outputItems(stack).buildRawRecipe();
        }
        return null;
    }

    private int getOreToProduce(OreVeinWorldEntry entry) {
        var definition = entry.getDefinition();
        if (definition != null) {
            int depletedYield = definition.depletedYield();
            int regularYield = entry.getOreYield();
            int remainingOperations = entry.getOperationsRemaining();
            int produced = Math.max(depletedYield, regularYield * remainingOperations / BedrockOreVeinSavedData.MAXIMUM_VEIN_OPERATIONS);
            produced *= BedrockOreMinerMachine.getRigMultiplier(getMachine().getTier());
            // Overclocks produce 50% more ore
            if (isOverclocked()) {
                produced = produced * 3 / 2;
            }
            return produced;
        }
        return 0;
    }

    public int getOreToProduce() {
        if (getMachine().getLevel() instanceof ServerLevel serverLevel && veinMaterials != null) {
            var data = BedrockOreVeinSavedData.getOrCreate(serverLevel);
            return getOreToProduce(data.getOreVeinWorldEntry(getChunkX(), getChunkZ()));
        }
        return 0;
    }

    @Override
    protected void onDrillFinish() {
        depleteVein();
    }

    protected void depleteVein() {
        if (getMachine().getLevel() instanceof ServerLevel serverLevel) {
            int chance = BedrockOreMinerMachine.getDepletionChance(getMachine().getTier());
            var data = BedrockOreVeinSavedData.getOrCreate(serverLevel);
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
    public List<WeightedMaterial> getVeinMaterials() {
        return this.veinMaterials;
    }
}
