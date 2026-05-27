package com.gregtechceu.gtceu.api.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiPart;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

public class WorkableMultiblockPartMachine extends MultiblockPartMachine implements IWorkableMultiPart {

    @Getter
    @Setter
    protected @Nullable RecipeHandlerUnit recipeHandlerUnit;

    public WorkableMultiblockPartMachine(MetaMachineBlockEntity holder) {
        super(holder);
    }
}
