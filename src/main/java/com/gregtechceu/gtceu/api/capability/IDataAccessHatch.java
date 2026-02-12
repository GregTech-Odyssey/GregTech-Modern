package com.gregtechceu.gtceu.api.capability;

import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;

import org.jetbrains.annotations.NotNull;

public interface IDataAccessHatch {

    /**
     * @param recipe the recipe to check
     * @return if the recipe is available for use
     */
    boolean isRecipeAvailable(@NotNull GTRecipeDefinition recipe);

    default void updateRecipeLogic() {}
}
