package com.gregtechceu.gtceu.api.recipe.modifier;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Represents a function that accepts a GTRecipe and returns a modified version of the GTRecipe, or null.
 * <p>
 * The passed recipe should NOT be modified.
 * If modifications are applied, a new GTRecipe object should be returned.
 * </p>
 *
 * <p>
 * This is a functional interface whose functional method is {@link #apply(GTRecipe)}
 * </p>
 */
@FunctionalInterface
public interface ModifierFunction {

    /**
     * Use this static to denote that the recipe should be cancelled
     */
    ModifierFunction NULL = recipe -> null;
    /**
     * Use this static to denote that the recipe doesn't get modified
     */
    ModifierFunction IDENTITY = recipe -> recipe;

    /**
     * Applies this modifier to the passed recipe
     * 
     * @param recipe the GTRecipe to apply the modifier to
     * @return A new GTRecipe object with modifications, or null if the recipe should be cancelled
     */
    @Contract(pure = true)
    @Nullable
    GTRecipe apply(@NotNull GTRecipe recipe);

    /**
     * Returns a composed function that first applies {@code before} to its input, then applies this function.
     * 
     * @param before the function to apply first
     * @return The composed function of {@code this.apply(before.apply(recipe))}
     */
    default ModifierFunction compose(@NotNull ModifierFunction before) {
        return recipe -> applySafe(before.apply(recipe));
    }

    /**
     * Returns a composed function that first applies this function to its input, then applies {@code after}
     * 
     * @param after the function to apply second
     * @return The composed function of {@code after.apply(this.apply(recipe))}
     */
    default ModifierFunction andThen(@NotNull ModifierFunction after) {
        return recipe -> after.applySafe(apply(recipe));
    }

    private GTRecipe applySafe(@Nullable GTRecipe recipe) {
        if (recipe == null) return null;
        return apply(recipe);
    }

    /**
     * Creates a FunctionBuilder to easily build a ModifierFunction that modifies parts of a recipe.
     * <p>
     * Note that <b>tick modifiers <em>do not</em> modify EUt contents</b> and that
     * <b>setting the OC level or parallel count <em>does not</em> modify the contents of the recipe.</b>
     * <p>
     * You should do that by setting the other parameters.
     * </p>
     * 
     * @return A new {@link ModifierFunction.FunctionBuilder} instance
     */
    static FunctionBuilder builder() {
        return new FunctionBuilder();
    }

    final class FunctionBuilder {

        private int parallels = 1;
        private int batchParallels = 1;
        private int addOCs = 0;
        private ContentModifier eutModifier = ContentModifier.IDENTITY;
        private ContentModifier durationModifier = ContentModifier.IDENTITY;
        private ContentModifier inputModifier = ContentModifier.IDENTITY;
        private ContentModifier outputModifier = ContentModifier.IDENTITY;
        private ContentModifier tickInputModifier = ContentModifier.IDENTITY;
        private ContentModifier tickOutputModifier = ContentModifier.IDENTITY;
        private final List<RecipeCondition> addedConditions = new ArrayList<>();

        public FunctionBuilder() {}

        public FunctionBuilder conditions(RecipeCondition... conditions) {
            addedConditions.addAll(Arrays.asList(conditions));
            return this;
        }

        public FunctionBuilder modifyAllContents(ContentModifier cm) {
            inputModifier = cm;
            outputModifier = cm;
            tickInputModifier = cm;
            tickOutputModifier = cm;
            return this;
        }

        public FunctionBuilder eutMultiplier(double multiplier) {
            eutModifier = ContentModifier.multiplier(multiplier);
            return this;
        }

        public FunctionBuilder durationMultiplier(double multiplier) {
            durationModifier = ContentModifier.multiplier(multiplier);
            return this;
        }

        /**
         * Builds the ModifierFunction from this builder.
         * <p>
         * Note that <b>tick modifiers <em>do not</em> modify EUt contents</b> and that
         * <b>setting the OC level or parallel count <em>does not</em> modify the contents of the recipe.</b>
         * <p>
         * You should do that by setting the other parameters.
         * </p>
         * 
         * @return A new {@link ModifierFunction} from the params of this builder
         */
        public ModifierFunction build() {
            if (parallels == 0) return NULL;
            return recipe -> {

                var copied = new GTRecipe(recipe.recipeType, inputModifier.copyContents(recipe.inputs), outputModifier.copyContents(recipe.outputs), applyAllButEU(tickInputModifier, recipe.tickInputs), applyAllButEU(tickOutputModifier, recipe.tickOutputs), recipe.data, recipe.duration, recipe.tier);
                copied.parallels = recipe.parallels * parallels;
                copied.ocLevel = recipe.ocLevel + addOCs;
                copied.batchParallels = recipe.batchParallels * batchParallels;
                if (recipe.data.getBoolean("duration_is_total_cwu")) {
                    copied.duration = (int) Math.max(1, (recipe.duration * (1.0F - 0.025F * addOCs)));
                } else {
                    copied.duration = Math.max(1, durationModifier.apply(recipe.duration));
                }
                if (eutModifier != ContentModifier.IDENTITY) {
                    long preEUt = RecipeHelper.getRealEUt(recipe);
                    long eut = Math.max(1, eutModifier.apply(Math.abs(preEUt)));
                    EURecipeCapability.putEUContent(preEUt > 0 ? copied.tickInputs : copied.tickOutputs, eut);
                }
                return copied;
            };
        }

        private static Map<RecipeCapability<?>, List<Content>> applyAllButEU(ContentModifier cm, Map<RecipeCapability<?>, List<Content>> contents) {
            if (cm == ContentModifier.IDENTITY) return new Reference2ReferenceOpenHashMap<>(contents);
            Map<RecipeCapability<?>, List<Content>> copyContents = new Reference2ReferenceOpenHashMap<>();
            for (var entry : contents.entrySet()) {
                var cap = entry.getKey();
                var contentList = entry.getValue();
                if (contentList != null && !contentList.isEmpty()) {
                    if (cap == EURecipeCapability.CAP) {
                        copyContents.put(cap, new ArrayList<>(contentList));
                        continue;
                    }
                    List<Content> contentsCopy = new ArrayList<>();
                    for (Content content : contentList) {
                        contentsCopy.add(content.copy(cap, cm));
                    }
                    copyContents.put(cap, contentsCopy);
                }
            }
            return copyContents;
        }

        /**
         * @return {@code this}.
         */
        public ModifierFunction.FunctionBuilder parallels(final int parallels) {
            this.parallels = parallels;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public ModifierFunction.FunctionBuilder batchParallels(final int batchParallels) {
            this.batchParallels = batchParallels;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public ModifierFunction.FunctionBuilder addOCs(final int addOCs) {
            this.addOCs = addOCs;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public ModifierFunction.FunctionBuilder eutModifier(final ContentModifier eutModifier) {
            this.eutModifier = eutModifier;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public ModifierFunction.FunctionBuilder durationModifier(final ContentModifier durationModifier) {
            this.durationModifier = durationModifier;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public ModifierFunction.FunctionBuilder inputModifier(final ContentModifier inputModifier) {
            this.inputModifier = inputModifier;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public ModifierFunction.FunctionBuilder outputModifier(final ContentModifier outputModifier) {
            this.outputModifier = outputModifier;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public ModifierFunction.FunctionBuilder tickInputModifier(final ContentModifier tickInputModifier) {
            this.tickInputModifier = tickInputModifier;
            return this;
        }

        /**
         * @return {@code this}.
         */
        public ModifierFunction.FunctionBuilder tickOutputModifier(final ContentModifier tickOutputModifier) {
            this.tickOutputModifier = tickOutputModifier;
            return this;
        }
    }
}
