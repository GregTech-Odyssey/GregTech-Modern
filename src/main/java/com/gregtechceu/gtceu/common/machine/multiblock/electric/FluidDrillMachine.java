package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.machine.trait.FluidDrillLogic;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FluidDrillMachine extends VeinDrillMachine<BedrockFluidDefinition> {

    public FluidDrillMachine(MetaMachineBlockEntity holder, int tier) {
        super(holder, tier, true);
    }

    @Override
    public RecipeLogic createRecipeLogic(Object... args) {
        return new FluidDrillLogic(this);
    }
}
