package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import com.fast.recipesearch.IntContainerHolder;
import com.fast.recipesearch.IntMapContainer;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class GTRecipe implements net.minecraft.world.item.crafting.Recipe<Container>, IntContainerHolder {

    public final GTRecipeType recipeType;
    public IntMapContainer container;
    @Getter
    @Setter
    public ResourceLocation id;
    public final Map<RecipeCapability<?>, List<Content>> inputs;
    public final Map<RecipeCapability<?>, List<Content>> outputs;
    public final Map<RecipeCapability<?>, List<Content>> tickInputs;
    public final Map<RecipeCapability<?>, List<Content>> tickOutputs;
    public final List<RecipeCondition> conditions;
    @NotNull
    public CompoundTag data;
    public int duration;
    public long parallels = 1;
    public int batchParallels = 1;
    public int ocLevel = 0;
    public final GTRecipeCategory recipeCategory;

    public GTRecipe(GTRecipeType recipeType, Map<RecipeCapability<?>, List<Content>> inputs, Map<RecipeCapability<?>, List<Content>> outputs, Map<RecipeCapability<?>, List<Content>> tickInputs, Map<RecipeCapability<?>, List<Content>> tickOutputs, CompoundTag data, int duration, GTRecipeCategory recipeCategory) {
        this(recipeType, null, inputs, outputs, tickInputs, tickOutputs, Collections.emptyList(), data, duration, recipeCategory);
    }

    public GTRecipe(GTRecipeType recipeType, @Nullable ResourceLocation id, Map<RecipeCapability<?>, List<Content>> inputs, Map<RecipeCapability<?>, List<Content>> outputs, Map<RecipeCapability<?>, List<Content>> tickInputs, Map<RecipeCapability<?>, List<Content>> tickOutputs, List<RecipeCondition> conditions, CompoundTag data, int duration, GTRecipeCategory recipeCategory) {
        this.recipeType = recipeType;
        this.id = id;
        this.inputs = inputs;
        this.outputs = outputs;
        this.tickInputs = tickInputs;
        this.tickOutputs = tickOutputs;
        this.conditions = conditions;
        this.data = data;
        this.duration = duration;
        this.recipeCategory = (recipeCategory != GTRecipeCategory.DEFAULT) ? recipeCategory : recipeType.getCategory();
    }

    public GTRecipe copy() {
        return copy(ContentModifier.IDENTITY, false);
    }

    public GTRecipe copy(ContentModifier modifier) {
        return copy(modifier, true);
    }

    public GTRecipe copy(ContentModifier modifier, boolean modifyDuration) {
        var copied = new GTRecipe(recipeType, id, modifier.applyContents(inputs), modifier.applyContents(outputs), modifier.applyContents(tickInputs), modifier.applyContents(tickOutputs), new ArrayList<>(conditions), data, duration, recipeCategory);
        if (modifyDuration) {
            copied.duration = modifier.apply(this.duration);
        }
        copied.ocLevel = ocLevel;
        copied.parallels = parallels;
        return copied;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return GTRecipeSerializer.SERIALIZER;
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
            eut += EURecipeCapability.CAP.of(content.content);
        }
        return eut;
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long getOutputEUt() {
        var outputs = tickOutputs.get(EURecipeCapability.CAP);
        if (outputs == null) return 0;
        long eut = 0;
        for (var content : outputs) {
            eut += EURecipeCapability.CAP.of(content.content);
        }
        return eut;
    }

    // Just check id as there *should* only ever be 1 instance of a recipe with this id.
    // If this doesn't work, fix.
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof GTRecipe recipe)) return false;
        return this.id.equals(recipe.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id.toString();
    }

    @Override
    public IntMapContainer getIntContainer() {
        return container;
    }

    @Override
    public void setIntContainer(IntMapContainer intMapContainer) {
        container = intMapContainer;
    }
}
