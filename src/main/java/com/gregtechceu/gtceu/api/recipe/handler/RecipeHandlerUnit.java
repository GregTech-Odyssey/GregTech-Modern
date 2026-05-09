package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.utils.collection.SafeR2LMap;
import com.gregtechceu.gtceu.utils.function.ObjLongPredicate;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.fast.recipesearch.IntLongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ObjLongConsumer;

public class RecipeHandlerUnit {

    public static Function<Object, RecipeHandlerUnit> WRAPPER = o -> new RecipeHandlerUnit(o instanceof RecipeHandlerUnit list ? list.handlerIO : (IO) o);

    public static final RecipeHandlerUnit NO_DATA = new RecipeHandlerUnit(IO.NONE);

    public final IMultiPart part;

    public final List<IRecipeHandler> itemHandlers = new ArrayList<>();
    public final List<IRecipeHandler> fluidHandlers = new ArrayList<>();
    public final List<IRecipeHandler> allHandlers = new ArrayList<>();
    public final List<NotifiableRecipeHandlerTrait<?>> allHandlerTraits = new ArrayList<>();

    @Getter
    protected final IO handlerIO;
    @Getter
    protected int color = -1;
    @Getter
    protected boolean isDistinct;

    public int priority;

    protected boolean isInfiniteOutputItem;
    protected boolean isInfiniteOutputFluid;

    // cache
    public final IntLongMap intIngredientMap = new IntLongMap();
    protected Reference2LongOpenHashMap<Item> itemMap;
    protected Reference2LongOpenHashMap<Fluid> fluidMap;

    protected RecipeHandlerUnit(IO handlerIO, IMultiPart part) {
        this.handlerIO = handlerIO;
        this.part = part;
    }

    private RecipeHandlerUnit(IO handlerIO) {
        this.handlerIO = handlerIO;
        this.part = null;
    }

    public static RecipeHandlerUnit of(IO io, IRecipeHandler... handlers) {
        RecipeHandlerUnit rhl = new RecipeHandlerUnit(io);
        rhl.addHandlers(handlers);
        return rhl;
    }

    public static RecipeHandlerUnit of(IO io, Collection<IRecipeHandler> handlers) {
        RecipeHandlerUnit rhl = new RecipeHandlerUnit(io);
        rhl.addHandlers(handlers);
        return rhl;
    }

    public static RecipeHandlerUnit of(IO io, IMultiPart part, Collection<IRecipeHandler> handlers) {
        RecipeHandlerUnit rhl = new RecipeHandlerUnit(io, part);
        rhl.addHandlers(handlers);
        return rhl;
    }

    public void addList(RecipeHandlerUnit list) {
        addHandlers(list.allHandlers);
    }

    public void addHandlers(IRecipeHandler... handlers) {
        addHandlers(Arrays.asList(handlers));
    }

    public void addHandlers(Collection<IRecipeHandler> handlers) {
        for (var handler : new ReferenceOpenHashSet<>(handlers)) {
            allHandlers.add(handler);
            if (handler.canHandleItem()) itemHandlers.add(handler);
            if (handler.canHandleFluid()) fluidHandlers.add(handler);
            if (!isInfiniteOutputItem && handler.isInfiniteOutputItem()) isInfiniteOutputItem = true;
            if (!isInfiniteOutputFluid && handler.isInfiniteOutputFluid()) isInfiniteOutputFluid = true;
            if (handler instanceof NotifiableRecipeHandlerTrait<?> rht) allHandlerTraits.add(rht);
        }
        allHandlers.sort(IRecipeHandler.PRIORITY_COMPARATOR);
        itemHandlers.sort(IRecipeHandler.PRIORITY_COMPARATOR);
        fluidHandlers.sort(IRecipeHandler.PRIORITY_COMPARATOR);
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

    public boolean isValid(IO extIO) {
        if (this == NO_DATA || handlerIO == IO.NONE) return false;
        return (extIO == IO.BOTH || handlerIO == IO.BOTH || extIO == handlerIO);
    }

    public ISubscription subscribe(Runnable listener) {
        List<ISubscription> subs = new ArrayList<>(allHandlerTraits.size());
        allHandlerTraits.forEach(rht -> subs.add(rht.addChangedListener(listener)));
        return () -> subs.forEach(ISubscription::unsubscribe);
    }

    public boolean findRecipe(GTRecipeType recipeType, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> canHandle) {
        var map = this.getIngredientMap(recipeType);
        if (map.isEmpty()) return false;
        return recipeType.search(this, map, canHandle);
    }

    public long getInputItemParallelAmount(List<Content<ItemIngredient>> contents, long multiplier) {
        if (itemMap == null) itemMap = new SafeR2LMap<>();
        var stacks = itemMap;
        for (var container : itemHandlers) {
            if (container.isNotConsumable()) continue;
            container.fastForEachItems((stack, amount) -> stacks.addTo(stack.getItem(), amount));
        }
        for (var content : contents) {
            if (content.chance > 0) {
                long needed = content.amount;
                if (needed < 1) continue;
                long available = 0;
                for (var iter = stacks.reference2LongEntrySet().fastIterator(); iter.hasNext();) {
                    var inventoryEntry = iter.next();
                    if (content.inner.testItem(inventoryEntry.getKey())) {
                        available += inventoryEntry.getLongValue();
                        if (available >= needed) break;
                    }
                }
                if (available >= needed) {
                    multiplier = Math.min(multiplier, available / needed);
                } else {
                    multiplier = 0;
                    break;
                }
            }
        }
        stacks.clear();
        return multiplier;
    }

    public long getInputFluidParallelAmount(List<Content<FluidIngredient>> contents, long multiplier) {
        if (fluidMap == null) fluidMap = new SafeR2LMap<>();
        var stacks = fluidMap;
        for (var container : fluidHandlers) {
            if (container.isNotConsumable()) continue;
            container.fastForEachFluids((stack, amount) -> stacks.addTo(stack.getFluid(), amount));
        }

        for (var content : contents) {
            if (content.chance > 0) {
                long needed = content.amount;
                if (needed < 1) continue;
                long available = 0;
                for (var it = stacks.reference2LongEntrySet().fastIterator(); it.hasNext();) {
                    var inventoryEntry = it.next();
                    if (content.inner.testFluid(inventoryEntry.getKey())) {
                        available = inventoryEntry.getLongValue();
                        break;
                    }
                }
                if (available >= needed) {
                    multiplier = Math.min(multiplier, available / needed);
                } else {
                    multiplier = 0;
                    break;
                }
            }
        }
        stacks.clear();
        return multiplier;
    }

    public long getOutputItemParallelAmount(GTRecipe recipe, List<Content<ItemIngredient>> contents, long multiplier) {
        if (isInfiniteOutputItem) return multiplier;
        long minMultiplier = 0;
        long maxMultiplier = multiplier;
        long maxCount = 0;
        for (var content : contents) {
            maxCount = Math.max(maxCount, content.amount);
        }
        if (maxCount == 0) return multiplier;
        if (multiplier > ParallelLogic.MAX_PARALLEL / maxCount) {
            maxMultiplier = multiplier = ParallelLogic.MAX_PARALLEL / maxCount;
        }
        var handlers = itemHandlers;
        if (handlers.isEmpty()) return 0;
        while (minMultiplier != maxMultiplier) {
            var copied = GTRecipe.copyContents(contents, multiplier);
            boolean success = false;
            for (var handler : handlers) {
                handler.handleRecipeItem(true, recipe, copied, true);
                if (copied.isEmpty()) {
                    success = true;
                    break;
                }
            }
            if (!success && multiplier == 1) {
                return 0;
            }
            long[] bin = adjustMultiplier(success, minMultiplier, multiplier, maxMultiplier);
            minMultiplier = bin[0];
            multiplier = bin[1];
            maxMultiplier = bin[2];
        }
        return multiplier;
    }

    public long getOutputFluidParallelAmount(GTRecipe recipe, List<Content<FluidIngredient>> contents, long multiplier) {
        if (isInfiniteOutputFluid) return multiplier;
        long minMultiplier = 0;
        long maxMultiplier = multiplier;
        long maxCount = 0;
        for (var content : contents) {
            maxCount = Math.max(maxCount, content.amount);
        }
        if (maxCount == 0) return multiplier;
        if (multiplier > ParallelLogic.MAX_PARALLEL / maxCount) {
            maxMultiplier = multiplier = ParallelLogic.MAX_PARALLEL / maxCount;
        }
        var handlers = fluidHandlers;
        if (handlers.isEmpty()) return 0;
        while (minMultiplier != maxMultiplier) {
            var copied = GTRecipe.copyContents(contents, multiplier);
            boolean success = false;
            for (var handler : handlers) {
                handler.handleRecipeFluid(true, recipe, copied, true);
                if (copied.isEmpty()) {
                    success = true;
                    break;
                }
            }
            if (!success && multiplier == 1) {
                return 0;
            }
            long[] bin = adjustMultiplier(success, minMultiplier, multiplier, maxMultiplier);
            minMultiplier = bin[0];
            multiplier = bin[1];
            maxMultiplier = bin[2];
        }
        return multiplier;
    }

    public IntLongMap getIngredientMap(@NotNull GTRecipeType type) {
        intIngredientMap.clear();
        allHandlers.forEach(handler -> handler.getIngredientMap(type).copyTo(intIngredientMap));
        return intIngredientMap;
    }

    public boolean forEachItems(ObjLongPredicate<ItemStack> function) {
        for (var handler : itemHandlers) {
            if (handler.forEachItems(function)) return true;
        }
        return false;
    }

    public boolean forEachFluids(ObjLongPredicate<FluidStack> function) {
        for (var handler : fluidHandlers) {
            if (handler.forEachFluids(function)) return true;
        }
        return false;
    }

    public void fastForEachItems(ObjLongConsumer<ItemStack> function) {
        itemHandlers.forEach(handler -> handler.fastForEachItems(function));
    }

    public void fastForEachFluids(ObjLongConsumer<FluidStack> function) {
        fluidHandlers.forEach(handler -> handler.fastForEachFluids(function));
    }

    public void fastForEach(ObjLongConsumer<ItemStack> itemFunction, ObjLongConsumer<FluidStack> FluidFunction) {
        for (var handler : allHandlerTraits) {
            handler.fastForEachItems(itemFunction);
            handler.fastForEachFluids(FluidFunction);
        }
    }

    public boolean handleRecipeItem(boolean output, GTRecipe recipe, List<Content<ItemIngredient>> items, boolean simulate) {
        if (items.isEmpty()) return true;
        if (output) {
            if (simulate && isInfiniteOutputItem) return true;
        } else {
            items = GTRecipe.copyContents(items, 1);
        }
        for (var handler : itemHandlers) {
            handler.handleRecipeItem(output, recipe, items, simulate);
            if (items.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean handleRecipeFluid(boolean output, GTRecipe recipe, List<Content<FluidIngredient>> fluids, boolean simulate) {
        if (fluids.isEmpty()) return true;
        if (output) {
            if (simulate && isInfiniteOutputFluid) return true;
        } else {
            fluids = GTRecipe.copyContents(fluids, 1);
        }
        for (var handler : fluidHandlers) {
            handler.handleRecipeFluid(output, recipe, fluids, simulate);
            if (fluids.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static long[] adjustMultiplier(boolean mergedAll, long minMultiplier, long multiplier, long maxMultiplier) {
        if (mergedAll) {
            minMultiplier = multiplier;
            long remainder = (maxMultiplier - multiplier) % 2;
            multiplier = multiplier + remainder + (maxMultiplier - multiplier) / 2;
        } else {
            maxMultiplier = multiplier;
            multiplier = (multiplier + minMultiplier) / 2;
        }
        if (maxMultiplier - minMultiplier <= 1) {
            multiplier = maxMultiplier = minMultiplier;
        }
        return new long[] { minMultiplier, multiplier, maxMultiplier };
    }
}
