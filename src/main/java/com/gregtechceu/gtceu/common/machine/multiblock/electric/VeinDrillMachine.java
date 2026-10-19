package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.data.worldgen.bedrock.BedrockVeinDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IVeinDrillMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.BedrockVeinDrillLogic;
import com.gregtechceu.gtceu.uiwidgets.multiblock.MultiblockPage;
import com.gregtechceu.gtceu.uiwidgets.vein.VeinDrillReadouts;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * MV/HV/EV multiblock drilling the bedrock vein below its controller; it may run one tier above its own for a bonus.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class VeinDrillMachine<D extends BedrockVeinDefinition> extends WorkableElectricMultiblockMachine implements IVeinDrillMachine {

    private final int drillTier;
    private final boolean fluid;

    protected VeinDrillMachine(MetaMachineBlockEntity holder, int drillTier, boolean fluid) {
        super(holder);
        this.drillTier = drillTier;
        this.fluid = fluid;
    }

    @Override
    public boolean supportLockRecipe() {
        return false;
    }

    @Override
    public boolean hasBatchConfig() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BedrockVeinDrillLogic<D> getRecipeLogic() {
        return (BedrockVeinDrillLogic<D>) super.getRecipeLogic();
    }

    @Override
    public int getDrillTier() {
        return drillTier;
    }

    @Override
    public int getEnergyTier() {
        return Math.min(drillTier + 1, tier);
    }

    @Override
    public int getTier() {
        return drillTier;
    }

    @Override
    protected boolean hasParallelReadout() {
        return false;
    }

    @Override
    public void addScreenReadouts(MultiblockPage page) {
        super.addScreenReadouts(page);
        VeinDrillReadouts.add(page, getRecipeLogic(), fluid);
    }
}
