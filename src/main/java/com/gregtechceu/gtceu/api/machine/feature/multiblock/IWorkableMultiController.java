package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.IRecipeHandler;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;

import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public interface IWorkableMultiController extends IMultiController, IRecipeLogicMachine {

    /**
     * The instance of {@link IParallelHatch} attached to this Controller.
     * <p>
     * Note that this will return a singular instance, and will not account for multiple attached IParallelHatches
     */
    @Nullable
    IParallelHatch getParallelHatch();

    /**
     *
     * @return Whether batching is enabled on this multiblock
     */
    default boolean isBatchEnabled() {
        return false;
    }

    default boolean hasBatchConfig() {
        return true;
    }

    @Override
    default boolean matchRecipeOutput(GTRecipe recipe) {
        List<Content<ItemIngredient>> items = canVoidRecipeOutputs(ItemRecipeCapability.CAP) ? Collections.emptyList() : GTRecipe.copyContents(recipe.itemOutputs, 1);
        List<Content<FluidIngredient>> fluids = canVoidRecipeOutputs(FluidRecipeCapability.CAP) ? Collections.emptyList() : GTRecipe.copyContents(recipe.fluidOutputs, 1);
        if (recipe.outputColor != -1) {
            var unit = getOutputColorMap().get(recipe.outputColor);
            if (unit != null) {
                return unit.handleRecipeItem(IO.OUT, recipe, items, true) && unit.handleRecipeFluid(IO.OUT, recipe, fluids, true);
            }
        }
        for (var handler : getOutputList()) {
            if (handler.handleRecipeItem(IO.OUT, recipe, items, true) && handler.handleRecipeFluid(IO.OUT, recipe, fluids, true)) {
                return true;
            }
        }
        return false;
    }

    @Override
    default boolean handleRecipeOutput(GTRecipe recipe) {
        var items = GTRecipe.copyContents(recipe.itemOutputs, 1);
        var fluids = GTRecipe.copyContents(recipe.fluidOutputs, 1);
        if (recipe.outputColor != -1) {
            var unit = getOutputColorMap().get(recipe.outputColor);
            if (unit != null) {
                return unit.handleRecipeItem(IO.OUT, recipe, items, false) && unit.handleRecipeFluid(IO.OUT, recipe, fluids, false);
            }
        }
        for (var handler : getOutputList()) {
            if (handler.handleRecipeItem(IO.OUT, recipe, items, false) && handler.handleRecipeFluid(IO.OUT, recipe, fluids, false)) {
                return true;
            }
        }
        return false;
    }

    default void arrangeHandlerList() {
        if (self().getLevel() instanceof ServerLevel serverLevel) {
            getRecipeLogic().markLastRecipeDirty();
            serverLevel.getServer().tell(new TickTask(1, getRecipeLogic()::updateTickSubscription));
        }
        getOutputColorMap().clear();
        var outputs = getCapabilitiesProxy().get(IO.OUT);
        if (outputs == null) {
            setOutputList(Collections.emptyList());
        } else {
            outputs.sort(RecipeHandlerUnit.PRIORITY_COMPARATOR);
            Int2ObjectOpenHashMap<List<RecipeHandlerUnit>> colour = new Int2ObjectOpenHashMap<>();
            List<RecipeHandlerUnit> untreated = new ArrayList<>();
            List<RecipeHandlerUnit> distinct = new ArrayList<>();
            for (var handler : RecipeHandlerUnit.filterContent(outputs)) {
                if (handler.part != null) {
                    var color = handler.part.self().getPaintingColor();
                    if (color != -1) {
                        colour.computeIfAbsent(color, k -> new ArrayList<>()).add(handler);
                        continue;
                    }
                }
                untreated.add(handler);
            }
            colour.int2ObjectEntrySet().fastForEach(e -> {
                var handlers = new ReferenceOpenHashSet<IRecipeHandler>();
                for (var list : e.getValue()) {
                    handlers.addAll(Arrays.asList(list.allHandlers));
                }
                var wrapper = e.getValue().getFirst().wrapper(handlers);
                var color = e.getIntKey();
                wrapper.setColor(color);
                distinct.add(wrapper);
                getOutputColorMap().put(color, wrapper);
            });
            if (!untreated.isEmpty()) {
                var handlers = new ReferenceOpenHashSet<IRecipeHandler>();
                for (var list : untreated) {
                    handlers.addAll(Arrays.asList(list.allHandlers));
                }
                var wrapper = untreated.getFirst().wrapper(handlers);
                distinct.add(wrapper);
            }
            distinct.sort(RecipeHandlerUnit.PRIORITY_COMPARATOR);
            setOutputList(distinct);
        }
        var inputs = getCapabilitiesProxy().get(IO.IN);
        if (inputs == null) {
            setInputList(Collections.emptyList());
        } else {
            inputs.sort(RecipeHandlerUnit.PRIORITY_COMPARATOR);
            Int2ObjectOpenHashMap<Map<IMultiPart, List<RecipeHandlerUnit>>> colour = new Int2ObjectOpenHashMap<>();
            List<RecipeHandlerUnit> untreated = new ArrayList<>();
            List<RecipeHandlerUnit> distinct = new ArrayList<>();
            for (var handler : RecipeHandlerUnit.filterContent(inputs)) {
                if (handler.part != null) {
                    var color = handler.part.self().getPaintingColor();
                    if (color != -1) {
                        colour.computeIfAbsent(color, k -> new Reference2ObjectOpenHashMap<>()).computeIfAbsent(handler.part, k -> new ArrayList<>()).add(handler);
                        continue;
                    }
                }
                untreated.add(handler);
            }
            colour.int2ObjectEntrySet().fastForEach(e -> {
                var map = e.getValue();
                var color = e.getIntKey();
                List<List<RecipeHandlerUnit>> same = new ArrayList<>(map.values());
                same.sort(Comparator.comparingInt(l -> -l.size()));
                var first = same.getFirst();
                for (int i = first.size() - 1; i >= 0; i--) {
                    var handlers = new ReferenceOpenHashSet<IRecipeHandler>();
                    var size = same.size();
                    for (int j = 1; j < size; j++) {
                        var other = same.get(j);
                        var otherList = other.get(Math.min(other.size() - 1, i));
                        handlers.addAll(Arrays.asList(otherList.allHandlers));
                    }
                    var wrapper = first.get(i).wrapper(handlers);
                    wrapper.setColor(color);
                    distinct.add(wrapper);
                }
            });
            if (!untreated.isEmpty()) {
                var it = untreated.iterator();
                while (it.hasNext()) {
                    var handler = it.next();
                    if (handler.isDistinct()) {
                        distinct.add(handler);
                        it.remove();
                    }
                }
                if (!untreated.isEmpty()) {
                    var handlers = new ReferenceOpenHashSet<IRecipeHandler>();
                    for (var handler : untreated) {
                        handlers.addAll(Arrays.asList(handler.allHandlers));
                    }
                    var wrapper = untreated.getFirst().wrapper(handlers);
                    distinct.add(wrapper);
                }
            }
            distinct.sort(RecipeHandlerUnit.PRIORITY_COMPARATOR);
            setInputList(distinct);
        }
    }

    Int2ReferenceOpenHashMap<RecipeHandlerUnit> getOutputColorMap();

    void setInputList(List<RecipeHandlerUnit> handlers);

    void setOutputList(List<RecipeHandlerUnit> handlers);

    IWorkableMultiPart[] getOnWorkingPart();

    IWorkableMultiPart[] getBeforeWorkingPart();

    IWorkableMultiPart[] getAfterWorkingPart();

    IWorkableMultiPart[] getModifyRecipePart();
}
