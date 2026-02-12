package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapabilityMap;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import com.fast.recipesearch.IntMapContainer;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.Range;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class GTRecipeDefinition implements net.minecraft.world.item.crafting.Recipe<Container> {

    IntMapContainer container;

    public final GTRecipeType recipeType;
    public final GTRecipeCategory recipeCategory;

    @Getter
    public final ResourceLocation id;

    public final Map<RecipeCapability<?>, List<Content>> inputs;
    public final Map<RecipeCapability<?>, List<Content>> outputs;
    public final Map<RecipeCapability<?>, List<Content>> tickInputs;
    public final Map<RecipeCapability<?>, List<Content>> tickOutputs;
    public final List<RecipeCondition> conditions;
    public final CompoundTag data;
    public final int duration;

    public GTRecipeDefinition(GTRecipeType recipeType, GTRecipeCategory recipeCategory, ResourceLocation id, Map<RecipeCapability<?>, List<Content>> inputs, Map<RecipeCapability<?>, List<Content>> outputs, Map<RecipeCapability<?>, List<Content>> tickInputs, Map<RecipeCapability<?>, List<Content>> tickOutputs, List<RecipeCondition> conditions, CompoundTag data, int duration) {
        this.recipeType = recipeType;
        this.recipeCategory = recipeCategory;
        this.id = id;
        this.inputs = inputs;
        this.outputs = outputs;
        this.tickInputs = tickInputs;
        this.tickOutputs = tickOutputs;
        this.conditions = conditions;
        this.data = data;
        this.duration = duration;
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

    public ChanceLogic getChanceLogicForCapability(RecipeCapability<?> cap, IO io) {
        return ChanceLogic.OR;
    }

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
    public RecipeSerializer<?> getSerializer() {
        return null;
    }

    @Override
    public GTRecipeType getType() {
        return recipeType;
    }

    @Override
    public boolean matches(Container pContainer, Level pLevel) {
        return false;
    }

    @Override
    public ItemStack assemble(Container inventory, RegistryAccess registryManager) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int pWidth, int pHeight) {
        return false;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryManager) {
        return ItemStack.EMPTY;
    }

    public GTRecipe toRuntime() {
        return new GTRecipe(recipeType, id, new RecipeCapabilityMap<>(inputs), new RecipeCapabilityMap<>(outputs), new Reference2ReferenceOpenHashMap<>(tickInputs), new Reference2ReferenceOpenHashMap<>(tickOutputs), data, duration, recipeCategory);
    }
}
