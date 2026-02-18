package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.nbt.CompoundTag;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class GTRecipe {

    public final GTRecipeDefinition definition;
    public final GTRecipeType recipeType;

    public final Map<RecipeCapability<?>, List<Content>> inputs;
    public final Map<RecipeCapability<?>, List<Content>> outputs;
    public final Map<RecipeCapability<?>, List<Content>> tickInputs;
    public final Map<RecipeCapability<?>, List<Content>> tickOutputs;
    @NotNull
    public CompoundTag data;
    public int tier;
    public int duration;
    public long parallels = 1;
    public int batchParallels = 1;
    public int ocLevel = 0;

    public int outputColor = -1;

    public GTRecipe(GTRecipeType recipeType, Map<RecipeCapability<?>, List<Content>> inputs, Map<RecipeCapability<?>, List<Content>> outputs, Map<RecipeCapability<?>, List<Content>> tickInputs, Map<RecipeCapability<?>, List<Content>> tickOutputs, CompoundTag data, int duration, int tier) {
        this(null, recipeType, inputs, outputs, tickInputs, tickOutputs, data, duration, tier);
    }

    public GTRecipe(@Nullable GTRecipeDefinition definition, GTRecipeType recipeType, Map<RecipeCapability<?>, List<Content>> inputs, Map<RecipeCapability<?>, List<Content>> outputs, Map<RecipeCapability<?>, List<Content>> tickInputs, Map<RecipeCapability<?>, List<Content>> tickOutputs, CompoundTag data, int duration, int tier) {
        this.definition = definition;
        this.recipeType = recipeType;
        this.inputs = inputs;
        this.outputs = outputs;
        this.tickInputs = tickInputs;
        this.tickOutputs = tickOutputs;
        this.data = data;
        this.duration = duration;
        this.tier = tier;
    }

    public GTRecipe copy() {
        return copy(ContentModifier.IDENTITY, false);
    }

    public GTRecipe copy(ContentModifier modifier) {
        return copy(modifier, true);
    }

    public GTRecipe copy(ContentModifier modifier, boolean modifyDuration) {
        var copied = new GTRecipe(recipeType, modifier.copyContents(inputs), modifier.copyContents(outputs), modifier.copy(tickInputs), modifier.copy(tickOutputs), data, duration, tier);
        if (modifyDuration) {
            copied.duration = modifier.apply(this.duration);
        }
        copied.ocLevel = ocLevel;
        copied.parallels = parallels;
        return copied;
    }

    public List<Content> getInputContents(RecipeCapability<?> capability) {
        return inputs.getOrDefault(capability, Collections.emptyList());
    }

    public List<Content> getOutputContents(RecipeCapability<?> capability) {
        return outputs.getOrDefault(capability, Collections.emptyList());
    }

    public List<Content> getTickInputContents(RecipeCapability<?> capability) {
        return tickInputs.getOrDefault(capability, Collections.emptyList());
    }

    public List<Content> getTickOutputContents(RecipeCapability<?> capability) {
        return tickOutputs.getOrDefault(capability, Collections.emptyList());
    }

    public boolean hasTick() {
        return !tickInputs.isEmpty() || !tickOutputs.isEmpty();
    }

    /**
     * Get the chance logic for a recipe capability + io + tick io combination
     *
     * @param cap the recipe capability to get the chance logic for
     * @param io  the {@link IO} of the chanche per-tick logic or the normal one
     * @return the chance logic for the aforementioned combination. Defaults to {@link ChanceLogic#OR}.
     */
    public ChanceLogic getChanceLogicForCapability(RecipeCapability<?> cap, IO io, boolean isTick) {
        return ChanceLogic.OR;
    }

    // Technically should account for overflow but realistically not an issue.
    @Range(from = 0, to = Long.MAX_VALUE)
    public long getInputEUt() {
        var inputs = tickInputs.get(EURecipeCapability.CAP);
        if (inputs == null) return 0;
        long eut = 0;
        for (var content : inputs) {
            eut += EURecipeCapability.CAP.of(content);
        }
        return eut;
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getOutputEUt() {
        var outputs = tickOutputs.get(EURecipeCapability.CAP);
        if (outputs == null) return 0;
        long eut = 0;
        for (var content : outputs) {
            eut += EURecipeCapability.CAP.of(content);
        }
        return eut;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GTRecipe recipe)) return false;
        return this.definition == recipe.definition;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(definition);
    }

    @Override
    public String toString() {
        return String.valueOf(definition);
    }
}
