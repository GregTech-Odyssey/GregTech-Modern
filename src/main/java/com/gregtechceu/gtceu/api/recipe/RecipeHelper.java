package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.chance.boost.ChanceBoostFunction;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gregtechceu.gtceu.utils.GTMath;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RecipeHelper {

    public static long getRealEUt(@NotNull GTRecipe recipe) {
        long EUt = recipe.getInputEUt();
        if (EUt > 0) return EUt;
        return -recipe.getOutputEUt();
    }

    public static int getRecipeEUtTier(GTRecipe recipe) {
        long EUt = recipe.getInputEUt();
        if (EUt == 0) EUt = recipe.getOutputEUt();
        if (recipe.parallels > 1) EUt /= recipe.parallels;
        return GTUtil.getTierByVoltage(EUt);
    }

    public static int getRecipeEUtTier(GTRecipeDefinition recipe) {
        long EUt = recipe.getInputEUt();
        if (EUt == 0) EUt = recipe.getOutputEUt();
        return GTUtil.getTierByVoltage(EUt);
    }

    public static int getPreOCRecipeEuTier(GTRecipe recipe) {
        long EUt = recipe.getInputEUt();
        if (EUt == 0) EUt = recipe.getOutputEUt();
        if (recipe.parallels > 1) EUt /= recipe.parallels;
        EUt >>= (recipe.ocLevel * 2);
        return GTUtil.getTierByVoltage(EUt);
    }

    public static <T> List<T> getInputContents(GTRecipe recipe, RecipeCapability<T> capability) {
        var inputs = recipe.inputs.get(capability);
        if (inputs == null) return Collections.emptyList();
        var result = new ArrayList<T>();
        for (var input : inputs) {
            result.add(capability.of(input));
        }
        return result;
    }

    public static <T> List<T> getOutputContents(GTRecipe recipe, RecipeCapability<T> capability) {
        var outputs = recipe.outputs.get(capability);
        if (outputs == null) return Collections.emptyList();
        var result = new ArrayList<T>();
        for (var output : outputs) {
            result.add(capability.of(output));
        }
        return result;
    }

    public static <T> List<T> getInputContents(GTRecipeBuilder builder, RecipeCapability<T> capability) {
        var inputs = builder.input.get(capability);
        if (inputs == null) return Collections.emptyList();
        var result = new ArrayList<T>();
        for (var input : inputs) {
            result.add(capability.of(input));
        }
        return result;
    }

    public static <T> List<T> getOutputContents(GTRecipeBuilder builder, RecipeCapability<T> capability) {
        var outputs = builder.output.get(capability);
        if (outputs == null) return Collections.emptyList();
        var result = new ArrayList<T>();
        for (var output : outputs) {
            result.add(capability.of(output));
        }
        return result;
    }

    public static boolean matchRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        return matchRecipe(holder, recipe, false);
    }

    public static boolean matchTickRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        return !recipe.hasTick() || matchRecipe(holder, recipe, true);
    }

    private static boolean matchRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe, boolean tick) {
        if (!holder.hasCapabilityProxies()) return false;

        var result = handleRecipe(holder, recipe, IO.IN, tick ? recipe.tickInputs : recipe.inputs,
                Collections.emptyMap(), true);
        if (!result) return result;

        result = handleRecipe(holder, recipe, IO.OUT, tick ? recipe.tickOutputs : recipe.outputs,
                Collections.emptyMap(), true);
        return result;
    }

    public static boolean handleRecipeIO(IRecipeCapabilityHolder holder, GTRecipe recipe, IO io,
                                         Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches) {
        if (!holder.hasCapabilityProxies() || io == IO.BOTH) return false;
        return handleRecipe(holder, recipe, io, io == IO.IN ? recipe.inputs : recipe.outputs, chanceCaches,
                false);
    }

    public static boolean handleTickRecipeIO(IRecipeCapabilityHolder holder, GTRecipe recipe, IO io,
                                             Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches) {
        if (!holder.hasCapabilityProxies() || io == IO.BOTH) return false;
        return handleRecipe(holder, recipe, io, io == IO.IN ? recipe.tickInputs : recipe.tickOutputs, chanceCaches,
                false);
    }

    /**
     * Checks if all the contents of the recipe are located in the holder.
     *
     * @param simulated checks that the recipe ingredients are in the holder if true,
     *                  process the recipe contents if false
     */
    public static boolean handleRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe, IO io, Map<RecipeCapability<?>, List<Content>> contents, Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches, boolean simulated) {
        if (contents.isEmpty()) return true;
        Reference2ReferenceOpenHashMap<RecipeCapability<?>, List<Object>> recipeContents = new Reference2ReferenceOpenHashMap<>();
        Reference2ReferenceOpenHashMap<RecipeCapability<?>, List<Object>> searchRecipeContents = simulated ? recipeContents : new Reference2ReferenceOpenHashMap<>();
        int recipeTier = getRecipeEUtTier(recipe);
        int chanceTier = recipeTier + recipe.ocLevel;
        for (Map.Entry<RecipeCapability<?>, List<Content>> entry : contents.entrySet()) {
            var cap = entry.getKey();
            var list = entry.getValue();
            if (list.isEmpty()) continue;
            List<Content> chancedContents = new ArrayList<>(list.size());
            var contentList = new ArrayList<>(list.size());
            var searchContentList = simulated ? null : new ArrayList<>(list.size());
            for (Content cont : list) {
                if (simulated) {
                    contentList.add(cont.inner);
                } else {
                    searchContentList.add(cont.inner);
                    if (cont.chance == 0) continue;
                    if (cont.chance >= ChanceLogic.getMaxChancedValue()) {
                        contentList.add(cont.inner);
                    } else {
                        chancedContents.add(cont);
                    }
                }
            }
            if (!chancedContents.isEmpty()) {
                for (var c : ChanceLogic.OR.roll(chancedContents, ChanceBoostFunction.OVERCLOCK, recipeTier, chanceTier, chanceCaches.get(cap), GTMath.saturatedCast(recipe.parallels))) {
                    contentList.add(c.inner);
                }
            }

            if (!contentList.isEmpty()) {
                recipeContents.put(cap, contentList);
            }
            if (!simulated && !searchContentList.isEmpty()) {
                searchRecipeContents.put(cap, searchContentList);
            }
        }

        if (searchRecipeContents.isEmpty()) return true;

        List<RecipeHandlerList> list;
        var handlers = holder.getCapabilitiesProxy().get(io);
        if (handlers == null) return false;
        list = handlers;

        for (var handler : list) {
            recipeContents = handleRecipe(handler, io, recipe, recipeContents, simulated);
            if (recipeContents.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    private static Reference2ReferenceOpenHashMap<RecipeCapability<?>, List<Object>> handleRecipe(RecipeHandlerList list, IO io, GTRecipe recipe, Reference2ReferenceOpenHashMap<RecipeCapability<?>, List<Object>> contents, boolean simulate) {
        if (list.handlerMap.isEmpty()) return contents;
        var copy = new Reference2ReferenceOpenHashMap<>(contents);
        for (var it = copy.reference2ReferenceEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            var handlerList = list.getCapability(entry.getKey());
            for (var handler : handlerList) {
                var left = handler.handleRecipe(io, recipe, entry.getValue(), simulate);
                if (left == null) {
                    it.remove();
                    break;
                } else {
                    entry.setValue(new ArrayList<>(left));
                }
            }
        }
        return copy;
    }

    public static boolean matchContents(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        return matchRecipe(holder, recipe) && matchTickRecipe(holder, recipe);
    }

    /**
     * Check whether all conditions of a recipe are valid
     *
     * @param recipe      the recipe to test
     * @param recipeLogic the logic to test against the conditions
     * @return the list of failed conditions, or success if all conditions are satisfied
     */
    public static ActionResult checkConditions(GTRecipeDefinition recipe, @NotNull RecipeLogic recipeLogic) {
        if (recipe.conditions.isEmpty()) return ActionResult.SUCCESS;
        Map<Class<?>, List<RecipeCondition>> or = new Reference2ObjectArrayMap<>();
        for (RecipeCondition condition : recipe.conditions) {
            if (condition.isOr()) {
                or.computeIfAbsent(condition.getClass(), type -> new ArrayList<>()).add(condition);
            } else if (!condition.check(recipe, recipeLogic)) {
                return ActionResult.fail(Component.translatable("gtceu.recipe_logic.condition_fails")
                        .append(": ")
                        .append(condition.getTooltips()));
            }
        }

        for (List<RecipeCondition> conditions : or.values()) {
            boolean passed = conditions.isEmpty();
            MutableComponent component = Component.translatable("gtceu.recipe_logic.condition_fails")
                    .append(": ");
            for (RecipeCondition condition : conditions) {
                passed = condition.check(recipe, recipeLogic);
                if (passed) break;
                else component.append(condition.getTooltips());
            }

            if (!passed) {
                return ActionResult.fail(component);
            }
        }
        return ActionResult.SUCCESS;
    }

    /**
     * Creates a copy of the recipe matching the trim limits -
     * Returns the recipe itself if no valid trim limits are passed
     */
    @Contract(pure = true)
    public static GTRecipe trimRecipeOutputs(GTRecipe recipe, Reference2IntOpenHashMap<RecipeCapability<?>> trimLimits) {
        // Fast return early if no trimming desired
        if (trimLimits.isEmpty()) return recipe;
        var outputs = doTrim(recipe.outputs, trimLimits);
        recipe.outputs.clear();
        recipe.outputs.putAll(outputs);
        return recipe;
    }

    /**
     * Returns the maximum possible recipe outputs from a recipe, divided into regular and chanced outputs
     * Takes into account any specific output limiters, ie macerator slots, to trim down the output list
     * Trims from chanced outputs first, then regular outputs
     *
     * @param trimLimits The limit(s) on the number of outputs
     * @return All recipe outputs, limited by some factor(s)
     */
    @Contract(pure = true)
    public static RecipeCapabilityMap<List<Content>> doTrim(Map<RecipeCapability<?>, List<Content>> current,
                                                            Reference2IntOpenHashMap<RecipeCapability<?>> trimLimits) {
        var outputs = new RecipeCapabilityMap<List<Content>>();

        for (var entry : current.entrySet()) {
            var cap = entry.getKey();
            var contents = entry.getValue();
            if (contents.isEmpty()) continue;
            int N = trimLimits.getOrDefault(cap, -1);
            if (N == 0) continue; // Skip this cap if limit is 0

            List<Content> list = outputs.computeIfAbsent(cap, c -> new ArrayList<>());
            if (N == -1) { // Add all if limit is -1/not in map
                list.addAll(contents);
                continue;
            }

            int added = 0;
            List<Content> chanced = new ArrayList<>();
            // Add non-chanced contents with priority and store chanced contents for later
            for (var content : contents) {
                if (added == N) break;
                if (0 < content.chance && content.chance < Content.MAX_CHANCE) {
                    chanced.add(content);
                } else {
                    list.add(content);
                    added++;
                }
            }

            // Add as many chanced contents as needed
            if (added < N) {
                int rem = Math.min(chanced.size(), N - added);
                list.addAll(chanced.subList(0, rem));
            }
        }

        return outputs;
    }
}
