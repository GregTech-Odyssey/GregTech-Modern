package com.gregtechceu.gtceu.common.machine.trait;

import com.gregtechceu.gtceu.api.data.worldgen.bedrock.BedrockVeinKind;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreDefinition;
import com.gregtechceu.gtceu.api.machine.trait.BedrockVeinDrillLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeBuilder;
import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.BedrockOreMinerMachine;
import com.gregtechceu.gtceu.uiwidgets.recipe.RecipeIOList;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BedrockOreMinerLogic extends BedrockVeinDrillLogic<BedrockOreDefinition> {

    public BedrockOreMinerLogic(BedrockOreMinerMachine machine) {
        super(machine, BedrockVeinKind.ORE);
    }

    @Nullable
    @Override
    protected GTRecipe buildDrillRecipe(BedrockOreDefinition vein, int amount) {
        var picked = GTUtil.getRandomItem(getMachine().getLevel().random, vein.materials());
        if (picked == null) return null;
        var stack = BedrockOreDefinition.dropOf(picked.material(), amount);
        if (stack.isEmpty()) return null;
        return GTRecipeBuilder.ofRaw().duration(MAX_PROGRESS).EUt(drillEUt()).outputItems(stack).buildRawRecipe();
    }

    @Override
    protected void addExpectedOutputs(List<RecipeIOList.Entry> outputs, BedrockOreDefinition vein, int produced) {
        int total = 0;
        for (var material : vein.materials()) total += material.weight();
        if (total <= 0) return;
        for (var material : vein.materials()) {
            var stack = BedrockOreDefinition.dropOf(material.material(), 1);
            if (stack.isEmpty()) continue;
            outputs.add(new RecipeIOList.Entry(true, stack, FluidStack.EMPTY, null, produced, (int) ((long) material.weight() * ContentList.MAX_CHANCE / total), false));
        }
    }
}
