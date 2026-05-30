package com.gregtechceu.gtceu.api.recipe.info;

public final class EURecipeInfo extends RecipeInfo {

    public final static EURecipeInfo INSTANCE = new EURecipeInfo();

    private EURecipeInfo() {
        super("eu", 0xFFFFFF00, false, 2);
    }
}
