package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapabilityMap;
import com.gregtechceu.gtceu.api.codec.data.DataKeys;
import com.gregtechceu.gtceu.api.codec.data.DataMap;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.TickContentMap;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import com.fast.recipesearch.IntMapContainer;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.Collections;
import java.util.List;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class GTRecipeDefinition {

    public static final GTRecipeDefinition DUMMY = new GTRecipeDefinition(false, GTRecipeTypes.DUMMY_RECIPES, GTCEu.id("dummy"), RecipeCapabilityMap.empty(), RecipeCapabilityMap.empty(), TickContentMap.EMPTY, Collections.emptyList(), DataMap.EMPTY, 0, 0, 0);

    IntMapContainer container;

    public final boolean registered;
    public final GTRecipeType recipeType;

    @Getter
    public final ResourceLocation id;

    public final RecipeCapabilityMap<List<Content>> inputs;
    public final RecipeCapabilityMap<List<Content>> outputs;
    public final TickContentMap ticks;
    public final List<RecipeCondition> conditions;
    public final DataMap data;
    public final int duration;
    public final int tier;
    public final int priority;

    public GTRecipeDefinition(boolean registered, GTRecipeType recipeType, ResourceLocation id, RecipeCapabilityMap<List<Content>> inputs, RecipeCapabilityMap<List<Content>> outputs, TickContentMap ticks, List<RecipeCondition> conditions, DataMap data, int duration, int tier, int priority) {
        this.registered = registered;
        this.recipeType = recipeType;
        this.id = id;
        this.inputs = inputs;
        this.outputs = outputs;
        this.ticks = ticks;
        this.conditions = conditions;
        this.data = data;
        this.duration = duration;
        this.tier = tier;
        this.priority = priority;
    }

    @Nullable
    public static GTRecipeDefinition get(ResourceLocation id) {
        return GTRecipeBuilder.RECIPE_MAP.get(id);
    }

    @Nullable
    public static GTRecipeDefinition get(String id) {
        return GTRecipeBuilder.RECIPE_MAP.get(ResourceLocation.tryParse(id));
    }

    @Nullable
    public static GTRecipeDefinition get(@Nullable Tag tag) {
        if (tag instanceof StringTag) return GTRecipeBuilder.RECIPE_MAP.get(ResourceLocation.tryParse(tag.getAsString()));
        return null;
    }

    public List<Content> getInputContents(RecipeCapability<?> capability) {
        return inputs.getOrDefault(capability, Collections.emptyList());
    }

    public List<Content> getOutputContents(RecipeCapability<?> capability) {
        return outputs.getOrDefault(capability, Collections.emptyList());
    }

    public ChanceLogic getChanceLogicForCapability(RecipeCapability<?> cap, IO io) {
        return ChanceLogic.OR;
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getInputEUt() {
        var eut = ticks.get(DataKeys.EUT);
        return eut > 0 ? eut : 0;
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getOutputEUt() {
        var eut = ticks.get(DataKeys.EUT);
        return eut < 0 ? -eut : 0;
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getCWUt() {
        return ticks.get(DataKeys.CWUT);
    }

    public GTRecipe toRuntime() {
        return new GTRecipe(this, new RecipeCapabilityMap<>(inputs), new RecipeCapabilityMap<>(outputs), new TickContentMap(ticks), duration, tier);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
