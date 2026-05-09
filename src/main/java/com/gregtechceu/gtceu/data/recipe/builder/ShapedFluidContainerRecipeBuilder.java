package com.gregtechceu.gtceu.data.recipe.builder;

import com.gregtechceu.gtceu.common.data.GTRecipes;
import com.gregtechceu.gtceu.common.recipe.ShapedFluidContainerRecipe;
import com.gregtechceu.gtceu.core.mixins.ShapedRecipeAccessor;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;

import org.jetbrains.annotations.Nullable;

public class ShapedFluidContainerRecipeBuilder extends ShapedRecipeBuilder {

    public ShapedFluidContainerRecipeBuilder(@Nullable ResourceLocation id) {
        super(id);
    }

    public void save() {
        var id = getId();
        var key = ShapedRecipeBuilder.symbolMapTokeys(symbolMap);
        String[] pattern = ShapedRecipeBuilder.shapeToPattern(shape);
        int xSize = pattern[0].length();
        int ySize = pattern.length;
        NonNullList<Ingredient> dissolved = ShapedRecipeAccessor.callDissolvePattern(pattern, key, xSize, ySize);
        GTRecipes.RECIPE_MAP.put(id, new ShapedFluidContainerRecipe(id, group, CraftingBookCategory.MISC, xSize, ySize, dissolved, output, true));
    }
}
