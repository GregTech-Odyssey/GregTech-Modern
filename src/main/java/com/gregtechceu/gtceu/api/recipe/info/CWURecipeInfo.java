package com.gregtechceu.gtceu.api.recipe.info;

public final class CWURecipeInfo extends RecipeInfo {

    public final static CWURecipeInfo INSTANCE = new CWURecipeInfo();

    private CWURecipeInfo() {
        super("cwu", 0xFFEEEE00, false, 3);
    }
}
