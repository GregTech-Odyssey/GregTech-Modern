package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeCapabilityHolder;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapabilityMap;
import com.gregtechceu.gtceu.api.machine.feature.IVoidable;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.machine.trait.RecipeHandlerList;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.chance.boost.ChanceBoostFunction;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import it.unimi.dsi.fastutil.longs.AbstractLong2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

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
        return holder.hasCapabilityProxies() && handleRecipe(holder, recipe, IO.IN, recipe.inputs, Collections.emptyMap(), true) && handleRecipe(holder, recipe, IO.OUT, recipe.outputs, Collections.emptyMap(), true);
    }

    public static boolean matchTickRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe) {
        return recipe.handleTickRecipe(holder, true);
    }

    public static boolean handleRecipeIO(IRecipeCapabilityHolder holder, GTRecipe recipe, IO io,
                                         Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches) {
        if (!holder.hasCapabilityProxies() || io == IO.BOTH) return false;
        return handleRecipe(holder, recipe, io, io == IO.IN ? recipe.inputs : recipe.outputs, chanceCaches, false);
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

    public static boolean handleRecipe(IRecipeCapabilityHolder holder, GTRecipe recipe, IO io, Map<RecipeCapability<?>, List<Content>> contents, Map<RecipeCapability<?>, Object2IntMap<?>> chanceCaches, boolean simulated) {
        if (contents.isEmpty()) return true;
        boolean in = io == IO.IN;
        RecipeHandlerList currentDistinc = in ? holder.getCurrentHandlerList() : null;
        IWorkableMultiController multiController = null;
        IVoidable voidable = null;
        if (holder instanceof IWorkableMultiController controller) {
            multiController = controller;
        }
        if (simulated && !in && holder instanceof IVoidable voidableHolder) {
            voidable = voidableHolder;
        }

        boolean search = in && !simulated && currentDistinc == null && multiController != null;

        RecipeCapabilityMap<List<Object>> recipeContents = new RecipeCapabilityMap<>();
        RecipeCapabilityMap<List<Object>> searchRecipeContents = search ? new RecipeCapabilityMap<>() : recipeContents;
        int chanceTier = recipe.tier + recipe.ocLevel;
        for (Map.Entry<RecipeCapability<?>, List<Content>> entry : contents.entrySet()) {
            var cap = entry.getKey();
            if (voidable != null && voidable.canVoidRecipeOutputs(cap)) continue;
            var list = entry.getValue();
            if (list.isEmpty()) continue;
            List<Content> chancedContents = new ArrayList<>(list.size());
            var contentList = new ArrayList<>(list.size());
            var searchContentList = search ? new ArrayList<>(list.size()) : null;
            for (Content cont : list) {
                if (simulated) {
                    contentList.add(cont.inner);
                } else {
                    if (search) searchContentList.add(cont.inner);
                    if (cont.chance == 0) continue;
                    if (cont.chance >= Content.MAX_CHANCE) {
                        contentList.add(cont.inner);
                    } else {
                        chancedContents.add(cont);
                    }
                }
            }
            if (!chancedContents.isEmpty()) {
                roll(cap, contentList, chancedContents, recipe.tier, chanceTier, chanceCaches.get(cap), recipe.parallels);
            }

            if (!contentList.isEmpty()) {
                recipeContents.put(cap, contentList);
            }
            if (search && !searchContentList.isEmpty()) {
                searchRecipeContents.put(cap, searchContentList);
            }
        }

        if (searchRecipeContents.isEmpty()) return true;

        if (currentDistinc != null) {
            if (currentDistinc.handleRecipeContent(io, recipe, recipeContents, simulated, simulated)) {
                recipe.outputColor = currentDistinc.getColor();
                return true;
            } else if (!simulated) {
                return false;
            }
        }

        List<RecipeHandlerList> list;
        if (multiController != null) {
            if (in) {
                list = holder.getInputList();
                int size = list.size() - 1;
                for (int i = 0; i <= size; i++) {
                    var handler = list.get(i);
                    if (i == size) {
                        if (handler.handleRecipeContent(io, recipe, recipeContents, simulated, true)) {
                            recipe.outputColor = handler.getColor();
                            if (simulated) {
                                holder.setCurrentHandlerList(handler);
                            }
                            return true;
                        }
                    } else {
                        if (handler.handleRecipeContent(io, recipe, searchRecipeContents, true, true)) {
                            recipe.outputColor = handler.getColor();
                            if (simulated) {
                                holder.setCurrentHandlerList(handler);
                                return true;
                            } else {
                                return handler.handleRecipeContent(io, recipe, recipeContents, false, false);
                            }
                        }
                    }
                }
                return false;
            } else {
                list = holder.getOutputList();
                if (recipe.outputColor != -1) {
                    var rhl = multiController.getOutputColorMap().get(recipe.outputColor);
                    if (rhl != null) {
                        return rhl.handleRecipeContent(io, recipe, recipeContents, simulated, false);
                    }
                }
            }
        } else {
            var handlers = holder.getCapabilitiesProxy().get(io);
            if (handlers == null) return false;
            list = handlers;
        }

        for (var handler : list) {
            if (handler.handleRecipeContent(io, recipe, recipeContents, simulated, false)) {
                if (in && simulated) holder.setCurrentHandlerList(handler);
                return true;
            }
        }

        return false;
    }

    private static void roll(RecipeCapability<?> cap, List<Object> contents, List<@NotNull Content> chancedEntries, int recipeTier, int chanceTier, @Nullable Object2IntMap<?> cache, long times) {
        List<AbstractLong2ObjectMap.BasicEntry<Object>> contentList = new ArrayList<>(chancedEntries.size());
        for (Content entry : chancedEntries) {
            long amount;
            if (entry instanceof LongSupplier separateContent) {
                times = separateContent.getAsLong();
            }

            int newChance = ChanceBoostFunction.OVERCLOCK.getBoostedChance(entry, recipeTier, chanceTier);
            long totalChance = times * newChance;
            amount = totalChance / Content.MAX_CHANCE;
            newChance = (int) totalChance % Content.MAX_CHANCE;

            int cached = getCachedChance(entry, cache);
            int chance = newChance + cached;
            while (chance >= Content.MAX_CHANCE) {
                amount++;
                chance -= Content.MAX_CHANCE;
                newChance -= Content.MAX_CHANCE;
            }
            if (cache != null) {
                // noinspection unchecked,rawtypes
                ((Object2IntMap) cache).put(entry.inner, chance);
            }
            if (amount > 0) {
                contentList.add(new AbstractLong2ObjectMap.BasicEntry<>(amount, entry.inner));
            }
        }
        for (var content : contentList) {
            if (content.getLongKey() > 1) {
                contents.add(cap.copyContent(content.getValue(), ContentModifier.multiplier(content.getLongKey())));
            } else {
                contents.add(content.getValue());
            }
        }
    }

    private static int getCachedChance(Content entry, @Nullable Object2IntMap<?> cache) {
        if (cache == null) return GTValues.RNG.nextInt(Content.MAX_CHANCE);
        int c = cache.getInt(entry.inner);
        if (c == 0) return GTValues.RNG.nextInt(Content.MAX_CHANCE);
        return c;
    }
}
