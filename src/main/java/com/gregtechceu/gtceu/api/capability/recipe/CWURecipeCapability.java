package com.gregtechceu.gtceu.api.capability.recipe;

public class CWURecipeCapability extends RecipeCapability<Long> {

    public final static CWURecipeCapability CAP = new CWURecipeCapability();

    protected CWURecipeCapability() {
        super("cwu", 0xFFEEEE00, false, 3);
    }
}
