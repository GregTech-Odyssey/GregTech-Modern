package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelFunction;
import com.gregtechceu.gtceu.utils.function.ObjLongPredicate;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.fast.recipesearch.IntLongMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.ObjLongConsumer;
import java.util.function.Predicate;

public class RecipeHandlerList {

    public static Function<Object, RecipeHandlerList> WRAPPER = o -> new RecipeHandlerList(o instanceof RecipeHandlerList list ? list.handlerIO : (IO) o);

    public static ParallelFunction<RecipeHandlerList> ITEM_PARALLEL = (holder, contents, parallelAmount, args) -> parallelAmount;
    public static ParallelFunction<RecipeHandlerList> FLUID_PARALLEL = (holder, contents, parallelAmount, args) -> parallelAmount;

    public static final RecipeHandlerList NO_DATA = new RecipeHandlerList(IO.NONE);

    public final Reference2ObjectOpenHashMap<RecipeCapability<?>, List<IRecipeHandler<?>>> handlerMap = new Reference2ObjectOpenHashMap<>();
    public final List<IRecipeHandler<?>> allHandlers = new ArrayList<>();
    public final List<NotifiableRecipeHandlerTrait<?>> allHandlerTraits = new ArrayList<>();
    @Getter
    private final IO handlerIO;
    @Getter
    private int color = -1;
    @Getter
    private boolean isDistinct;

    public int priority;

    public final IntLongMap intIngredientMap = new IntLongMap();

    public final IMultiPart part;

    protected RecipeHandlerList(IO handlerIO, IMultiPart part) {
        this.handlerIO = handlerIO;
        this.part = part;
    }

    private RecipeHandlerList(IO handlerIO) {
        this.handlerIO = handlerIO;
        this.part = null;
    }

    public static RecipeHandlerList of(IO io, IRecipeHandler<?>... handlers) {
        RecipeHandlerList rhl = new RecipeHandlerList(io);
        rhl.addHandlers(handlers);
        return rhl;
    }

    public static RecipeHandlerList of(IO io, Iterable<IRecipeHandler<?>> handlers) {
        RecipeHandlerList rhl = new RecipeHandlerList(io);
        rhl.addHandlers(handlers);
        return rhl;
    }

    public static RecipeHandlerList of(IO io, IMultiPart part, Iterable<IRecipeHandler<?>> handlers) {
        RecipeHandlerList rhl = new RecipeHandlerList(io, part);
        rhl.addHandlers(handlers);
        return rhl;
    }

    public void addList(RecipeHandlerList list) {
        addHandlers(list.allHandlers);
    }

    public void addHandlers(IRecipeHandler<?>... handlers) {
        addHandlers(Arrays.asList(handlers));
    }

    public void addHandlers(Iterable<IRecipeHandler<?>> handlers) {
        for (var handler : handlers) {
            if (allHandlers.contains(handler)) continue;
            handlerMap.computeIfAbsent(handler.getCapability(), c -> new ArrayList<>()).add(handler);
            allHandlers.add(handler);
            if (handler instanceof NotifiableRecipeHandlerTrait<?> rht) allHandlerTraits.add(rht);
        }
        allHandlers.sort(IRecipeHandler.PRIORITY_COMPARATOR);
        for (var list : handlerMap.values()) {
            list.sort(IRecipeHandler.PRIORITY_COMPARATOR);
        }
    }

    public final void setDistinctAndNotify(boolean distinct) {
        setDistinct(distinct);
        if (part != null) {
            notify(part);
        }
    }

    public final void setDistinct(boolean distinct) {
        if (isDistinct != distinct) {
            isDistinct = distinct;
            for (var rht : allHandlerTraits) {
                rht.setDistinct(isDistinct);
            }
        }
    }

    public void setColor(int color) {
        setColor(color, false);
    }

    public void setColor(int color, boolean notify) {
        this.color = color;
        if (notify && part != null) {
            notify(part);
        }
    }

    public static void notify(IMultiPart part) {
        for (IMultiController controller : part.getControllers()) {
            if (controller instanceof IWorkableMultiController workableMultiController) {
                workableMultiController.arrangeHandlerList();
            }
        }
    }

    public boolean hasCapability(RecipeCapability<?> cap) {
        return handlerMap.containsKey(cap);
    }

    public @NotNull List<IRecipeHandler<?>> getCapability(RecipeCapability<?> cap) {
        return handlerMap.getOrDefault(cap, Collections.emptyList());
    }

    public boolean isValid(IO extIO) {
        if (this == NO_DATA || handlerIO == IO.NONE) return false;
        return (extIO == IO.BOTH || handlerIO == IO.BOTH || extIO == handlerIO);
    }

    public ISubscription subscribe(Runnable listener) {
        List<ISubscription> subs = new ArrayList<>(allHandlerTraits.size());
        allHandlerTraits.forEach(rht -> subs.add(rht.addChangedListener(listener)));
        return () -> subs.forEach(ISubscription::unsubscribe);
    }

    public ISubscription subscribe(Runnable listener, RecipeCapability<?> cap) {
        var capList = getCapability(cap);
        List<ISubscription> subs = new ArrayList<>(capList.size());
        for (var handler : capList) {
            if (handler instanceof IRecipeHandlerTrait<?> trait) {
                subs.add(trait.addChangedListener(listener));
            }
        }
        return () -> subs.forEach(ISubscription::unsubscribe);
    }

    public boolean findRecipe(IRecipeCapabilityHolder holder, GTRecipeType recipeType, Predicate<GTRecipeDefinition> canHandle) {
        var map = this.getIngredientMap(recipeType);
        if (map.isEmpty()) return false;
        holder.setCurrentHandlerList(this);
        return recipeType.search(map, canHandle);
    }

    public long getInputItemParallel(IRecipeLogicMachine holder, List<Content> contents, long parallelAmount) {
        return ITEM_PARALLEL.getParallel(holder, contents, parallelAmount, this);
    }

    public long getInputFluidParallel(IRecipeLogicMachine holder, List<Content> contents, long parallelAmount) {
        return FLUID_PARALLEL.getParallel(holder, contents, parallelAmount, this);
    }

    public IntLongMap getIngredientMap(@NotNull GTRecipeType type) {
        intIngredientMap.clear();
        allHandlers.forEach(handler -> handler.getIngredientMap(type).copyTo(intIngredientMap));
        return intIngredientMap;
    }

    public boolean forEachItems(ObjLongPredicate<ItemStack> function) {
        for (var handler : getCapability(ItemRecipeCapability.CAP)) {
            if (handler.forEachItems(function)) return true;
        }
        return false;
    }

    public boolean forEachFluids(ObjLongPredicate<FluidStack> function) {
        for (var handler : getCapability(FluidRecipeCapability.CAP)) {
            if (handler.forEachFluids(function)) return true;
        }
        return false;
    }

    public void fastForEachItems(ObjLongConsumer<ItemStack> function) {
        for (var handler : getCapability(ItemRecipeCapability.CAP)) {
            handler.fastForEachItems(function);
        }
    }

    public void fastForEachFluids(ObjLongConsumer<FluidStack> function) {
        for (var handler : getCapability(FluidRecipeCapability.CAP)) {
            handler.fastForEachFluids(function);
        }
    }

    public void fastForEach(ObjLongConsumer<ItemStack> itemFunction, ObjLongConsumer<FluidStack> FluidFunction) {
        for (var handler : allHandlerTraits) {
            handler.fastForEachItems(itemFunction);
            handler.fastForEachFluids(FluidFunction);
        }
    }

    public static List<RecipeHandlerList> filter(List<RecipeHandlerList> list) {
        List<RecipeHandlerList> output = new ArrayList<>();
        for (var handler : list) {
            if (!handler.allHandlers.isEmpty() && handler.hasCapability(ItemRecipeCapability.CAP) || handler.hasCapability(FluidRecipeCapability.CAP)) {
                output.add(handler);
            }
        }
        return output;
    }
}
