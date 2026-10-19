package com.gregtechceu.gtceu.common.machine.trait;

import com.gregtechceu.gtceu.api.data.worldgen.bedrock.BedrockVeinKind;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.api.machine.trait.BedrockVeinDrillLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeBuilder;
import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FluidDrillMachine;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeIOList;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public class FluidDrillLogic extends BedrockVeinDrillLogic<BedrockFluidDefinition> {

    public FluidDrillLogic(FluidDrillMachine machine) {
        super(machine, BedrockVeinKind.FLUID);
    }

    @Override
    protected GTRecipe buildDrillRecipe(BedrockFluidDefinition vein, int amount) {
        return GTRecipeBuilder.ofRaw().duration(MAX_PROGRESS).EUt(drillEUt()).outputFluids(new FluidStack(vein.fluid(), amount)).buildRawRecipe();
    }

    @Override
    protected void addExpectedOutputs(List<RecipeIOList.Entry> outputs, BedrockFluidDefinition vein, int produced) {
        expectedOutputs(outputs, vein, produced);
    }

    public static void expectedOutputs(List<RecipeIOList.Entry> outputs, BedrockFluidDefinition vein, int produced) {
        outputs.add(new RecipeIOList.Entry(true, ItemStack.EMPTY, new FluidStack(vein.fluid(), 1), null, produced, ContentList.MAX_CHANCE, false));
    }
}
