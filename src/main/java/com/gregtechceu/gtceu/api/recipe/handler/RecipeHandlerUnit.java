package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.machine.trait.CircuitHandler;
import com.gregtechceu.gtceu.api.machine.trait.IRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.utils.FluidStackHashStrategy;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;
import com.gregtechceu.gtceu.utils.collection.SafeR2LMap;
import com.gregtechceu.gtceu.utils.function.ObjLongPredicate;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.fast.recipesearch.IntLongMap;
import com.gto.datasynclib.util.holder.LongHolder;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.ObjLongConsumer;

public class RecipeHandlerUnit {

    public static final Comparator<RecipeHandlerUnit> PRIORITY_COMPARATOR = Comparator.comparingInt(u -> -u.priority);

    public static final RecipeHandlerUnit NO_DATA = new RecipeHandlerUnit(IO.NONE, null) {

        @Override
        public boolean handleRecipeItem(IO io, GTRecipe recipe, List<Content<ItemIngredient>> items, boolean simulate) {
            return items.isEmpty();
        }

        @Override
        public boolean handleRecipeFluid(IO io, GTRecipe recipe, List<Content<FluidIngredient>> fluids, boolean simulate) {
            return fluids.isEmpty();
        }
    };;

    public final IMultiPart part;

    public final IRecipeHandler[] itemHandlers;
    public final IRecipeHandler[] fluidHandlers;
    public final IRecipeHandler[] contentHandlers;
    public final IRecipeHandler[] allHandlers;
    public final IRecipeHandlerTrait[] allHandlerTraits;

    public final IO handlerIO;
    public int color = -1;
    public boolean isDistinct;

    public int priority;

    public boolean isInfiniteOutputItem;
    public boolean isInfiniteOutputFluid;

    // cache
    protected final IntLongMap intIngredientMap = new IntLongMap();
    protected Reference2LongOpenHashMap<Item> itemMap;
    protected Reference2LongOpenHashMap<Fluid> fluidMap;

    protected RecipeHandlerUnit(IO handlerIO, IMultiPart part, IRecipeHandler... handlers) {
        this.handlerIO = handlerIO;
        this.part = part;
        Arrays.sort(handlers, IFilteredHandler.PRIORITY_COMPARATOR);
        this.allHandlers = handlers;
        var items = new ArrayList<IRecipeHandler>();
        var fluids = new ArrayList<IRecipeHandler>();
        var searchs = new ArrayList<IRecipeHandler>();
        var traits = new ArrayList<IRecipeHandlerTrait>();
        for (var handler : handlers) {
            if (handler.canHandleItem()) {
                if (handler.isInfiniteOutputItem()) {
                    isInfiniteOutputItem = true;
                    priority = IFilteredHandler.HIGH;
                }
                items.add(handler);
            }
            if (handler.canHandleFluid()) {
                if (handler.isInfiniteOutputFluid()) {
                    isInfiniteOutputFluid = true;
                    priority = IFilteredHandler.HIGH;
                }
                fluids.add(handler);
            }
            if (handler.canHandleContent()) searchs.add(handler);
            if (handler instanceof IRecipeHandlerTrait trait) {
                if (trait.getHandlerIO() != handlerIO) throw new IllegalArgumentException("RecipeHandlerTrait IO must match RecipeHandlerUnit IO");
                traits.add(trait);
            }
        }
        this.itemHandlers = items.toArray(new IRecipeHandler[0]);
        this.fluidHandlers = fluids.toArray(new IRecipeHandler[0]);
        this.contentHandlers = searchs.toArray(new IRecipeHandler[0]);
        this.allHandlerTraits = traits.toArray(new IRecipeHandlerTrait[0]);
    }

    public static RecipeHandlerUnit of(IO io, IRecipeHandler... handlers) {
        return new RecipeHandlerUnit(io, null, handlers);
    }

    public static RecipeHandlerUnit of(IO io, Collection<IRecipeHandler> handlers) {
        return new RecipeHandlerUnit(io, null, handlers.toArray(new IRecipeHandler[0]));
    }

    public static RecipeHandlerUnit of(IO io, IMultiPart part, Collection<IRecipeHandler> handlers) {
        return new RecipeHandlerUnit(io, part, handlers.toArray(new IRecipeHandler[0]));
    }

    public static List<RecipeHandlerUnit> filterContent(Collection<RecipeHandlerUnit> handlers) {
        var list = new ArrayList<RecipeHandlerUnit>();
        handlers.forEach(h -> {
            if (h.contentHandlers.length > 0) list.add(h);
        });
        return list;
    }

    public RecipeHandlerUnit wrapper(Collection<IRecipeHandler> handlers) {
        var u = of(this.handlerIO, handlers);
        u.priority = this.priority;
        return u;
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
        return extIO == handlerIO;
    }

    public ISubscription subscribe(Runnable listener) {
        ISubscription[] subs = new ISubscription[allHandlerTraits.length];
        for (int i = 0; i < subs.length; i++) {
            subs[i] = allHandlerTraits[i].addChangedListener(listener);
        }
        return () -> {
            for (var s : subs) {
                s.unsubscribe();
            }
        };
    }

    public <T> ISubscription subscribe(Runnable listener, Class<T> capabilitie) {
        var subs = new ArrayList<ISubscription>();
        for (IRecipeHandlerTrait trait : allHandlerTraits) {
            if (capabilitie.isInstance(trait)) {
                subs.add(trait.addChangedListener(listener));
            }
        }
        return () -> subs.forEach(ISubscription::unsubscribe);
    }

    @NotNull
    public <T> List<T> getCapabilities(Class<T> capabilitie) {
        var all = allHandlers;
        if (all.length == 0) return Collections.emptyList();
        var list = new ArrayList<T>();
        for (var handler : all) {
            if (capabilitie.isInstance(handler)) {
                list.add((T) handler);
            }
        }
        return list;
    }

    public boolean findRecipe(GTRecipeType recipeType, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> canHandle) {
        var map = this.getSearchMap(recipeType);
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

    public IntLongMap getSearchMap(@NotNull GTRecipeType type) {
        intIngredientMap.clear();
        for (var s : contentHandlers) {
            s.getSearchMap(type).copyTo(intIngredientMap);
        }
        return intIngredientMap;
    }

    public boolean forEachItems(boolean consumable, ObjLongPredicate<ItemStack> function) {
        for (var handler : itemHandlers) {
            if (consumable && handler.isNotConsumable()) continue;
            if (handler.forEachItems(function)) return true;
        }
        return false;
    }

    public boolean forEachFluids(boolean consumable, ObjLongPredicate<FluidStack> function) {
        for (var handler : fluidHandlers) {
            if (consumable && handler.isNotConsumable()) continue;
            if (handler.forEachFluids(function)) return true;
        }
        return false;
    }

    public void fastForEachItems(boolean consumable, ObjLongConsumer<ItemStack> function) {
        for (var handler : itemHandlers) {
            if (consumable && handler.isNotConsumable()) continue;
            handler.fastForEachItems(function);
        }
    }

    public void fastForEachFluids(boolean consumable, ObjLongConsumer<FluidStack> function) {
        for (var handler : fluidHandlers) {
            if (consumable && handler.isNotConsumable()) continue;
            handler.fastForEachFluids(function);
        }
    }

    public void fastForEach(boolean consumable, ObjLongConsumer<ItemStack> itemFunction, ObjLongConsumer<FluidStack> FluidFunction) {
        for (var handler : allHandlers) {
            if (consumable && handler.isNotConsumable()) continue;
            handler.fastForEachItems(itemFunction);
            handler.fastForEachFluids(FluidFunction);
        }
    }

    public boolean handleRecipeItem(IO io, GTRecipe recipe, List<Content<ItemIngredient>> items, boolean simulate) {
        if (items.isEmpty()) return true;
        if (io != handlerIO) throw new IllegalStateException("IO is not the same");
        if (io == IO.OUT && simulate && isInfiniteOutputItem) return true;
        for (var handler : itemHandlers) {
            if (!simulate && handler.isNotConsumable()) continue;
            handler.handleRecipeItem(io, recipe, items, simulate);
            if (items.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean handleRecipeFluid(IO io, GTRecipe recipe, List<Content<FluidIngredient>> fluids, boolean simulate) {
        if (fluids.isEmpty()) return true;
        if (io != handlerIO) throw new IllegalStateException("IO is not the same");
        if (io == IO.OUT && simulate && isInfiniteOutputFluid) return true;
        for (var handler : fluidHandlers) {
            if (!simulate && handler.isNotConsumable()) continue;
            handler.handleRecipeFluid(io, recipe, fluids, simulate);
            if (fluids.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean handleItem(IO io, List<Content<ItemIngredient>> items, boolean simulate) {
        return handleRecipeItem(io, GTRecipe.EMPTY, items, simulate);
    }

    public boolean handleFluid(IO io, List<Content<FluidIngredient>> fluids, boolean simulate) {
        return handleRecipeFluid(io, GTRecipe.EMPTY, fluids, simulate);
    }

    public boolean inputItem(ItemLike item, long amount) {
        var contentList = new ArrayList<Content<ItemIngredient>>(1);
        contentList.add(new Content<>(ItemIngredient.of(item, amount)));
        return handleItem(IO.IN, RecipeHelper.copyContents(contentList, 1), true) && handleItem(IO.IN, contentList, false);
    }

    public boolean inputItem(ItemStack... items) {
        var contentList = toItemIngredient(items);
        return handleItem(IO.IN, RecipeHelper.copyContents(contentList, 1), true) && handleItem(IO.IN, contentList, false);
    }

    public boolean simulateOutputItem(ItemLike item, long amount) {
        var contentList = new ArrayList<Content<ItemIngredient>>(1);
        contentList.add(new Content<>(ItemIngredient.of(item, amount)));
        return handleItem(IO.OUT, contentList, true);
    }

    public boolean simulateOutputItem(ItemStack... items) {
        var contentList = toItemIngredient(items);
        return handleItem(IO.OUT, contentList, true);
    }

    public boolean outputItem(ItemLike item, long amount) {
        var contentList = new ArrayList<Content<ItemIngredient>>(1);
        contentList.add(new Content<>(ItemIngredient.of(item, amount)));
        return handleItem(IO.OUT, contentList, false);
    }

    public boolean outputItem(ItemStack... items) {
        var contentList = toItemIngredient(items);
        return handleItem(IO.OUT, contentList, false);
    }

    public boolean matchItem(ItemLike item) {
        return matchItem(item, 1);
    }

    public boolean matchItem(ItemLike item, long amount) {
        var i = item.asItem();
        var holder = new LongHolder(amount);
        return forEachItems(false, (stack, a) -> {
            if (stack.is(i)) {
                holder.value -= a;
                return holder.value <= 0;
            }
            return false;
        });
    }

    public boolean matchItem(ItemStack... items) {
        for (var item : items) {
            var holder = new LongHolder(item.getCount());
            if (!forEachItems(false, (stack, a) -> {
                if (ItemStackHashStrategy.ITEM_AND_TAG.equals(stack, item)) {
                    holder.value -= a;
                    return holder.value <= 0;
                }
                return false;
            })) {
                return false;
            }
        }
        return true;
    }

    public boolean matchCircuit(int configuration) {
        for (var iRecipeHandler : itemHandlers) {
            if (iRecipeHandler instanceof CircuitHandler circuitHandler) {
                var itemStack = circuitHandler.storage.stacks[0];
                if (itemStack.is(IntCircuitIngredient.PROGRAMMED_CIRCUIT) && configuration == IntCircuitIngredient.getConfiguration(itemStack.getTag())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean inputFluid(Fluid fluid, long amount) {
        var contentList = new ArrayList<Content<FluidIngredient>>(1);
        contentList.add(new Content<>(FluidIngredient.of(fluid, amount)));
        return handleFluid(IO.IN, RecipeHelper.copyContents(contentList, 1), true) && handleFluid(IO.IN, contentList, false);
    }

    public boolean inputFluid(FluidStack... fluids) {
        var contentList = toFluidIngredient(fluids);
        return handleFluid(IO.IN, RecipeHelper.copyContents(contentList, 1), true) && handleFluid(IO.IN, contentList, false);
    }

    public boolean simulateOutputFluid(Fluid fluid, long amount) {
        var contentList = new ArrayList<Content<FluidIngredient>>(1);
        contentList.add(new Content<>(FluidIngredient.of(fluid, amount)));
        return handleFluid(IO.OUT, contentList, true);
    }

    public boolean simulateOutputFluid(FluidStack... fluids) {
        var contentList = toFluidIngredient(fluids);
        return handleFluid(IO.OUT, contentList, true);
    }

    public boolean outputFluid(Fluid fluid, long amount) {
        var contentList = new ArrayList<Content<FluidIngredient>>(1);
        contentList.add(new Content<>(FluidIngredient.of(fluid, amount)));
        return handleFluid(IO.OUT, contentList, false);
    }

    public boolean outputFluid(FluidStack... fluids) {
        var contentList = toFluidIngredient(fluids);
        return handleFluid(IO.OUT, contentList, false);
    }

    public boolean matchFluid(Fluid fluid) {
        return matchFluid(fluid, 1);
    }

    public boolean matchFluid(Fluid fluid, long amount) {
        var holder = new LongHolder(amount);
        return forEachFluids(false, (stack, a) -> {
            if (stack.getFluid() == fluid) {
                holder.value -= a;
                return holder.value <= 0;
            }
            return false;
        });
    }

    public boolean matchFluid(FluidStack... fluids) {
        for (var fluid : fluids) {
            var holder = new LongHolder(fluid.getAmount());
            if (!forEachFluids(false, (stack, a) -> {
                if (FluidStackHashStrategy.FLUID_AND_TAG.equals(stack, fluid)) {
                    holder.value -= a;
                    return holder.value <= 0;
                }
                return false;
            })) {
                return false;
            }
        }
        return true;
    }

    public int getCircuit(boolean sum) {
        int circuit = 0;
        for (var iRecipeHandler : itemHandlers) {
            if (iRecipeHandler instanceof CircuitHandler circuitHandler) {
                var itemStack = circuitHandler.storage.stacks[0];
                if (itemStack.is(IntCircuitIngredient.PROGRAMMED_CIRCUIT)) {
                    var c = IntCircuitIngredient.getConfiguration(itemStack.getTag());
                    if (c > 0) {
                        circuit += c;
                        if (!sum) break;
                    }
                }
            }
        }
        return circuit;
    }

    public long[] getItemAmount(boolean consumable, Item... items) {
        long[] amounts = new long[items.length];
        getItemAmount(consumable, items, amounts);
        return amounts;
    }

    public long[] getFluidAmount(boolean consumable, Fluid... fluids) {
        long[] amounts = new long[fluids.length];
        getFluidAmount(consumable, fluids, amounts);
        return amounts;
    }

    public void getItemAmount(boolean consumable, Item[] items, long[] amounts) {
        fastForEachItems(consumable, (stack, amount) -> {
            var item = stack.getItem();
            for (int i = 0; i < items.length; i++) {
                if (items[i] == item) {
                    var a = amount + amounts[i];
                    if (a < 0) {
                        amounts[i] = Long.MAX_VALUE;
                    } else {
                        amounts[i] = a;
                    }
                    break;
                }
            }
        });
    }

    public void getFluidAmount(boolean consumable, Fluid[] fluids, long[] amounts) {
        fastForEachFluids(consumable, (stack, amount) -> {
            var fluid = stack.getFluid();
            for (int i = 0; i < fluids.length; i++) {
                if (fluids[i] == fluid) {
                    var a = amount + amounts[i];
                    if (a < 0) {
                        amounts[i] = Long.MAX_VALUE;
                    } else {
                        amounts[i] = a;
                    }
                    break;
                }
            }
        });
    }

    public static List<Content<ItemIngredient>> toItemIngredient(ItemStack... item) {
        var contentList = new ArrayList<Content<ItemIngredient>>(item.length);
        for (var content : item) {
            if (content.isEmpty()) continue;
            contentList.add(new Content<>(ItemIngredient.of(content)));
        }
        return contentList;
    }

    public static List<Content<FluidIngredient>> toFluidIngredient(FluidStack... fluid) {
        var contentList = new ArrayList<Content<FluidIngredient>>(fluid.length);
        for (FluidStack content : fluid) {
            if (content.isEmpty()) continue;
            contentList.add(new Content<>(FluidIngredient.of(content)));
        }
        return contentList;
    }
}
