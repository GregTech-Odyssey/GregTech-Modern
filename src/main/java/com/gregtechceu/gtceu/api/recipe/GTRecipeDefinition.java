package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.recipe.content.ChanceBoostFunction;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.expand.ContentExpander;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.resources.ResourceLocation;

import com.fast.recipesearch.IntMapContainer;
import com.gto.datasynclib.datasream.DataComponentMap;
import org.jetbrains.annotations.Range;

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
    public final ContentExpander[] contentExpanders;
    public final ContentExpander[] tickContentExpanders;
    public final DataComponentMap data;
    public final ChanceBoostFunction chanceFunction;
    public final long eut;
    public final int tier;
    public final int duration;
    public final int priority;

    public GTRecipeDefinition(boolean registered, GTRecipeType recipeType, GTRecipeCategory recipeCategory, ResourceLocation id, List<Content<ItemIngredient>> itemInputs, List<Content<ItemIngredient>> itemOutputs, List<Content<FluidIngredient>> fluidInputs, List<Content<FluidIngredient>> fluidOutputs, List<RecipeCondition> conditions, List<ContentExpander> contentExpanders, List<ContentExpander> tickContentExpanders, DataComponentMap data, ChanceBoostFunction chanceFunction, long eut, int tier, int duration, int priority) {
        this.registered = registered;
        this.recipeType = recipeType;
        this.recipeCategory = recipeCategory;
        this.id = id;
        this.itemInputs = itemInputs;
        this.itemOutputs = itemOutputs;
        this.fluidInputs = fluidInputs;
        this.fluidOutputs = fluidOutputs;
        this.conditions = conditions.toArray(new RecipeCondition[0]);
        this.contentExpanders = contentExpanders.toArray(new ContentExpander[0]);
        this.tickContentExpanders = tickContentExpanders.toArray(new ContentExpander[0]);
        this.data = data;
        this.chanceFunction = chanceFunction;
        this.eut = eut;
        this.tier = tier;
        this.duration = duration;
        this.priority = priority;
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
        return new GTRecipe(this, itemInputs, itemOutputs, fluidInputs, fluidOutputs, data.clone(), eut, tier, duration);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
