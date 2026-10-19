package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreDefinition;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.common.machine.trait.BedrockOreMinerLogic;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class BedrockOreMinerMachine extends VeinDrillMachine<BedrockOreDefinition> {

    public BedrockOreMinerMachine(MetaMachineBlockEntity holder, int tier) {
        super(holder, tier, false);
    }

    @Override
    public RecipeLogic createRecipeLogic(Object... args) {
        return new BedrockOreMinerLogic(this);
    }
}
