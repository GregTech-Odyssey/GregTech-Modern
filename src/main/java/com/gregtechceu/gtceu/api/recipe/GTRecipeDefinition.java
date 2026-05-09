package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.expand.ContentExpand;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;

import com.fast.recipesearch.IntMapContainer;
import com.gto.datasynclib.datasream.DataComponentMap;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class GTRecipeDefinition {

    IntMapContainer container;

    public final boolean registered;
    public final GTRecipeType recipeType;
    public final GTRecipeCategory recipeCategory;

    public final ResourceLocation id;

    public final List<Content<ItemIngredient>> itemInputs;
    public final List<Content<ItemIngredient>> itemOutputs;
    public final List<Content<FluidIngredient>> fluidInputs;
    public final List<Content<FluidIngredient>> fluidOutputs;
    public final RecipeCondition[] conditions;
    public final ContentExpand[] contentExpands;
    public final ContentExpand[] tickContentExpands;
    public final DataComponentMap data;
    public final long eut;
    public final int tier;
    public final int duration;

    public GTRecipeDefinition(boolean registered, GTRecipeType recipeType, GTRecipeCategory recipeCategory, ResourceLocation id, List<Content<ItemIngredient>> itemInputs, List<Content<ItemIngredient>> itemOutputs, List<Content<FluidIngredient>> fluidInputs, List<Content<FluidIngredient>> fluidOutputs, List<RecipeCondition> conditions, List<ContentExpand> contentExpands, List<ContentExpand> tickContentExpands, DataComponentMap data, long eut, int tier, int duration) {
        this.registered = registered;
        this.recipeType = recipeType;
        this.recipeCategory = recipeCategory;
        this.id = id;
        this.itemInputs = itemInputs;
        this.itemOutputs = itemOutputs;
        this.fluidInputs = fluidInputs;
        this.fluidOutputs = fluidOutputs;
        this.conditions = conditions.toArray(new RecipeCondition[0]);
        this.contentExpands = contentExpands.toArray(new ContentExpand[0]);
        this.tickContentExpands = tickContentExpands.toArray(new ContentExpand[0]);
        this.data = data;
        this.eut = eut;
        this.tier = tier;
        this.duration = duration;
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getInputEUt() {
        var eu = eut;
        if (eu > 0) return eu;
        return 0;
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getOutputEUt() {
        var eu = eut;
        if (eu < 0) return -eu;
        return 0;
    }

    public GTRecipe toRuntime() {
        return new GTRecipe(this, new ArrayList<>(itemInputs), new ArrayList<>(itemOutputs), new ArrayList<>(fluidInputs), new ArrayList<>(fluidOutputs), data.clone(), eut, duration, tier);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
