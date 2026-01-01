package com.gregtechceu.gtceu.api.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;

import net.minecraft.MethodsReturnNonnullByDefault;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class WorkableTieredIOPartMachine extends TieredIOPartMachine implements IWorkableMultiPart {

    @Getter
    @Setter
    protected @Nullable RecipeHandlerList recipeHandlerList;

    public WorkableTieredIOPartMachine(MetaMachineBlockEntity holder, int tier, IO io) {
        super(holder, tier, io);
    }
}
