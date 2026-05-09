package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.capability.IParallelHatch;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerUnit;

import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
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

    default void arrangeHandlerList() {
        if (self().getLevel() instanceof ServerLevel serverLevel) {
            getRecipeLogic().markLastRecipeDirty();
            serverLevel.getServer().tell(new TickTask(1, getRecipeLogic()::updateTickSubscription));
        }
        getOutputColorMap().clear();
        setCurrentHandlerList(null);
        var outputs = getCapabilitiesProxy().get(IO.OUT);
        if (outputs == null) {
            setOutputList(Collections.emptyList());
        } else {
            Int2ObjectOpenHashMap<List<RecipeHandlerUnit>> colour = new Int2ObjectOpenHashMap<>();
            List<RecipeHandlerUnit> untreated = new ArrayList<>();
            List<RecipeHandlerUnit> distinct = new ArrayList<>();
            for (var handler : RecipeHandlerUnit.filter(outputs)) {
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
                var rhl = RecipeHandlerUnit.WRAPPER.apply(IO.OUT);
                for (var list : e.getValue()) {
                    rhl.addHandlers(list.allHandlers);
                }
                var color = e.getIntKey();
                rhl.setColor(color);
                distinct.add(rhl);
                getOutputColorMap().put(color, rhl);
            });
            var indistinct = RecipeHandlerUnit.WRAPPER.apply(IO.OUT);
            for (var list : untreated) {
                indistinct.addHandlers(list.allHandlers);
            }
            if (!indistinct.allHandlers.isEmpty()) distinct.add(indistinct);
            distinct.sort(Comparator.comparingInt(a -> -a.priority));
            setOutputList(distinct);
        }
        var inputs = getCapabilitiesProxy().get(IO.IN);
        if (inputs == null) {
            setInputList(Collections.emptyList());
        } else {
            Int2ObjectOpenHashMap<Map<IMultiPart, List<RecipeHandlerUnit>>> colour = new Int2ObjectOpenHashMap<>();
            List<RecipeHandlerUnit> untreated = new ArrayList<>();
            List<RecipeHandlerUnit> distinct = new ArrayList<>();
            for (var handler : RecipeHandlerUnit.filter(inputs)) {
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
                    var list = first.get(i);
                    var wrapper = RecipeHandlerUnit.WRAPPER.apply(list);
                    wrapper.addList(list);
                    var size = same.size();
                    for (int j = 1; j < size; j++) {
                        var other = same.get(j);
                        var otherList = other.get(Math.min(other.size() - 1, i));
                        wrapper.addList(otherList);
                    }
                    wrapper.setColor(color);
                    distinct.add(wrapper);
                }
            });
            var indistinct = RecipeHandlerUnit.WRAPPER.apply(IO.IN);
            for (var handler : untreated) {
                if (handler.isDistinct()) {
                    distinct.add(handler);
                } else {
                    indistinct.addHandlers(handler.allHandlers);
                }
            }
            if (!indistinct.allHandlers.isEmpty()) distinct.add(indistinct);
            distinct.sort(Comparator.comparingInt(a -> -a.priority));
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
