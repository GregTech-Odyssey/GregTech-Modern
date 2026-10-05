package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.machine.trait.IRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableContentHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.CircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.content.Circuits;
import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.recipe.content.ContentRoll;
import com.gregtechceu.gtceu.api.recipe.content.FluidTagIngredient;
import com.gregtechceu.gtceu.api.recipe.content.ItemTagIngredient;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import com.gto.recipesearch.IntLongMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * 处理器分组：同一方向的一组 {@link IRecipeHandler} 作为一个共享存储池参与配方。
 * 输入先规划（只读、零分配）再提交：可失败成员先提交并可回退，数组型存储最后按预留日志扣减，保证不吞料、不复制。
 */
public class RecipeHandlerUnit {

    public static final Comparator<RecipeHandlerUnit> PRIORITY_COMPARATOR = (a, b) -> Integer.compare(b.priority, a.priority);
    public static final Comparator<RecipeHandlerUnit> TYPE_COMPARATOR = (a, b) -> {
        var aClass = a.getClass();
        var bClass = b.getClass();
        if (aClass == bClass) return Integer.compare(b.priority, a.priority);
        if (aClass == RecipeHandlerUnit.class) return 1;
        if (bClass == RecipeHandlerUnit.class) return -1;
        int cmp = Integer.compare(b.priority, a.priority);
        if (cmp != 0) return cmp;
        return aClass.getName().compareTo(bClass.getName());
    };

    private static final KeyInventory<?>[] NO_STORES = new KeyInventory<?>[0];

    public static final RecipeHandlerUnit NO_DATA = new RecipeHandlerUnit(IO.NONE, null);

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
    public boolean isInfiniteItemCapacity;
    public boolean isInfiniteFluidCapacity;

    private final KeyInventory<?>[] itemStores;
    private final KeyInventory<?>[] fluidStores;
    protected final IntLongMap intIngredientMap = new IntLongMap();
    private final boolean searchCacheable;
    private GTRecipeType searchType;
    private int searchStamp;
    private int[] searchKeys;

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
            var p = handler.getPriority();
            if (this.priority < p) this.priority = p;
            if (handler.handlesItems()) {
                if (handler.isInfiniteCapacity(AEKeyTypes.ITEMS)) isInfiniteItemCapacity = true;
                items.add(handler);
            }
            if (handler.handlesFluids()) {
                if (handler.isInfiniteCapacity(AEKeyTypes.FLUIDS)) isInfiniteFluidCapacity = true;
                fluids.add(handler);
            }
            if (handler.isSearchable()) searchs.add(handler);
            if (handler instanceof IRecipeHandlerTrait trait) {
                if (trait.getHandlerIO() != handlerIO) throw new IllegalArgumentException("RecipeHandlerTrait IO must match RecipeHandlerUnit IO");
                traits.add(trait);
            }
        }
        this.itemHandlers = items.toArray(new IRecipeHandler[0]);
        this.fluidHandlers = fluids.toArray(new IRecipeHandler[0]);
        this.itemStores = stores(itemHandlers, AEKeyTypes.ITEMS);
        this.fluidStores = stores(fluidHandlers, AEKeyTypes.FLUIDS);
        this.contentHandlers = searchs.toArray(new IRecipeHandler[0]);
        this.allHandlerTraits = traits.toArray(new IRecipeHandlerTrait[0]);
        boolean cacheable = true;
        for (var handler : contentHandlers) {
            if (!(handler instanceof NotifiableContentHandler)) {
                cacheable = false;
                break;
            }
        }
        this.searchCacheable = cacheable;
    }

    private static KeyInventory<?>[] stores(IRecipeHandler[] members, AEKeyType type) {
        int n = members.length;
        if (n == 0) return NO_STORES;
        var stores = new KeyInventory<?>[n];
        for (int h = 0; h < n; h++) stores[h] = members[h].storage(type);
        return stores;
    }

    public void refreshPriority() {
        if (allHandlers.length == 0) return;
        int max = Integer.MIN_VALUE;
        for (var handler : allHandlers) {
            var p = handler.getPriority();
            if (max < p) max = p;
        }
        this.priority = max;
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
        for (var h : handlers) {
            if (h.contentHandlers.length > 0) list.add(h);
        }
        return list;
    }

    public RecipeHandlerUnit wrapper(Collection<IRecipeHandler> handlers) {
        var u = of(this.handlerIO, handlers);
        u.priority = this.priority;
        return u;
    }

    public final void setDistinctAndNotify(boolean distinct) {
        setDistinct(distinct);
        if (part != null) notify(part);
    }

    public final void setDistinct(boolean distinct) {
        if (isDistinct != distinct) isDistinct = distinct;
    }

    public void setColor(int color) {
        setColor(color, false);
    }

    public void setColor(int color, boolean notify) {
        this.color = color;
        if (notify && part != null) notify(part);
    }

    public static void notify(IMultiPart part) {
        part.self().onChanged();
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
            for (var s : subs) s.unsubscribe();
        };
    }

    public <T> ISubscription subscribe(Runnable listener, Class<T> capability) {
        var subs = new ArrayList<ISubscription>(allHandlerTraits.length);
        for (IRecipeHandlerTrait trait : allHandlerTraits) {
            if (capability.isInstance(trait)) subs.add(trait.addChangedListener(listener));
        }
        return () -> subs.forEach(ISubscription::unsubscribe);
    }

    @NotNull
    public <T> List<T> getCapabilities(RecipeInfo key, Class<T> capability) {
        var all = key == ItemRecipeInfo.INSTANCE ? itemHandlers : key == FluidRecipeInfo.INSTANCE ? fluidHandlers : allHandlers;
        return filter(all, capability);
    }

    @NotNull
    public <T> List<T> getCapabilities(Class<T> capability) {
        return filter(allHandlers, capability);
    }

    private static <T> List<T> filter(IRecipeHandler[] all, Class<T> capability) {
        if (all.length == 0) return Collections.emptyList();
        var list = new ArrayList<T>(all.length);
        for (var handler : all) {
            if (capability.isInstance(handler)) list.add(capability.cast(handler));
        }
        return list;
    }

    public boolean findRecipe(GTRecipeType recipeType, BiPredicate<RecipeHandlerUnit, GTRecipeDefinition> canHandle) {
        var map = this.getSearchMap(recipeType);
        if (map.isEmpty()) return false;
        return recipeType.search(this, map, canHandle);
    }

    public IntLongMap getSearchMap(@NotNull GTRecipeType type) {
        var map = intIngredientMap;
        var handlers = contentHandlers;
        int stamp = 0;
        if (searchCacheable) {
            for (var s : handlers) {
                var h = (NotifiableContentHandler) s;
                h.getSearchMap(type);
                stamp += h.searchMapVersion();
            }
            if (searchType == type && searchStamp == stamp) return map;
        }
        searchKeys = null;
        map.clear();
        for (var s : handlers) {
            s.addToSearchMap(map, type);
        }
        if (searchCacheable) {
            searchType = type;
            searchStamp = stamp;
        }
        return map;
    }

    public int[] searchKeys(IntLongMap map) {
        if (map != intIngredientMap) return map.toIntArray();
        var keys = searchKeys;
        if (keys == null) searchKeys = keys = map.toIntArray();
        return keys;
    }

    public void onCommitted(GTRecipe recipe) {
        for (var h : allHandlers) h.onRecipeCommitted(recipe);
    }

    private void beginPlan(PlanScratch p) {
        var items = itemStores;
        var fluids = fluidStores;
        int itemCount = items.length;
        int fluidCount = fluids.length;
        p.ensureMembers(itemCount, fluidCount);
        var offsets = p.itemOffsets;
        var versions = p.itemVersions;
        int slots = 0;
        for (int h = 0; h < itemCount; h++) {
            var inv = items[h];
            offsets[h] = slots;
            if (inv != null) {
                versions[h] = inv.version();
                slots += inv.size();
            }
        }
        offsets = p.fluidOffsets;
        versions = p.fluidVersions;
        for (int h = 0; h < fluidCount; h++) {
            var inv = fluids[h];
            offsets[h] = slots;
            if (inv != null) {
                versions[h] = inv.version();
                slots += inv.size();
            }
        }
        p.begin(slots);
    }

    private static boolean planInputList(PlanScratch p, ContentList list, IRecipeHandler[] members, KeyInventory<?>[] stores, int[] offsets, AEKeyType type, long @Nullable [] needs, byte fluidFlag, long scale, boolean emptyRecipe) {
        int n = list.size();
        if (n == 0) return true;
        int memberCount = members.length;
        int[] order = list.planOrder();
        for (int o = 0; o < n; o++) {
            int i = order[o];
            boolean consume = list.isConsumable(i);
            long need = consume ? (needs != null ? needs[i] : list.effective(i, scale)) : list.amount(i);
            if (need <= 0) continue;
            var ing = list.ingredient(i);
            byte flags = consume ? (byte) (fluidFlag | PlanScratch.FLAG_CONSUME) : fluidFlag;
            for (int h = 0; h < memberCount && need > 0; h++) {
                var m = members[h];
                if (consume && m.isNotConsumable()) continue;
                if (emptyRecipe && m.isOnlyRecipe()) continue;
                var inv = stores[h];
                if (inv == null) {
                    need -= m.reserveInput(p, h, type, i, ing, need, consume);
                } else if (!consume && m.isPresenceOnly()) {
                    if (sumArray(inv, ing, 1) > 0) need = 0;
                } else {
                    need -= reserveArray(p, h, offsets[h], inv, ing, i, need, flags);
                }
            }
            if (need > 0) return false;
        }
        return true;
    }

    private static long reserveArray(PlanScratch p, int member, int offset, KeyInventory<?> inv, KeyIngredient ing, int entry, long need, byte flags) {
        Object o = ing;
        if (o instanceof AEItemKey k) return k.hasTag() ? reserveKey(p, member, offset, inv, k, entry, need, flags) : reserveUid(p, member, offset, inv, k.uid, entry, need, flags);
        if (o instanceof AEFluidKey k) return k.hasTag() ? reserveKey(p, member, offset, inv, k, entry, need, flags) : reserveUid(p, member, offset, inv, k.uid, entry, need, flags);
        if (o instanceof ItemTagIngredient t) return reserveItemTag(p, member, offset, inv, t.tag, entry, need, flags);
        if (o instanceof FluidTagIngredient t) return reserveFluidTag(p, member, offset, inv, t.tag, entry, need, flags);
        if (o instanceof CircuitIngredient c) return reserveCircuit(p, member, offset, inv, c.config, entry, need, flags);
        return reserveTest(p, member, offset, inv, ing, entry, need, flags);
    }

    private static long reserveKey(PlanScratch p, int member, int offset, KeyInventory<?> inv, AEKey key, int entry, long need, byte flags) {
        int size = inv.size();
        long got = 0;
        for (int s = 0; s < size && got < need; s++) {
            long a = inv.amountAt(s);
            if (a > 0 && inv.rawKeyAt(s) == key) got += take(p, member, offset + s, s, a, need - got, entry, flags);
        }
        return got;
    }

    private static long reserveUid(PlanScratch p, int member, int offset, KeyInventory<?> inv, int uid, int entry, long need, byte flags) {
        int size = inv.size();
        long got = 0;
        for (int s = 0; s < size && got < need; s++) {
            long a = inv.amountAt(s);
            if (a > 0 && inv.uidAt(s) == uid) got += take(p, member, offset + s, s, a, need - got, entry, flags);
        }
        return got;
    }

    private static long reserveItemTag(PlanScratch p, int member, int offset, KeyInventory<?> inv, TagKey<Item> tag, int entry, long need, byte flags) {
        int size = inv.size();
        long got = 0;
        for (int s = 0; s < size && got < need; s++) {
            long a = inv.amountAt(s);
            if (a > 0 && ((AEItemKey) inv.rawKeyAt(s)).item.builtInRegistryHolder().is(tag)) got += take(p, member, offset + s, s, a, need - got, entry, flags);
        }
        return got;
    }

    private static long reserveFluidTag(PlanScratch p, int member, int offset, KeyInventory<?> inv, TagKey<Fluid> tag, int entry, long need, byte flags) {
        int size = inv.size();
        long got = 0;
        for (int s = 0; s < size && got < need; s++) {
            long a = inv.amountAt(s);
            if (a > 0 && ((AEFluidKey) inv.rawKeyAt(s)).fluid.is(tag)) got += take(p, member, offset + s, s, a, need - got, entry, flags);
        }
        return got;
    }

    private static long reserveCircuit(PlanScratch p, int member, int offset, KeyInventory<?> inv, int config, int entry, long need, byte flags) {
        int size = inv.size();
        int uid = Circuits.uid();
        long got = 0;
        for (int s = 0; s < size && got < need; s++) {
            long a = inv.amountAt(s);
            if (a > 0 && inv.uidAt(s) == uid && Circuits.configOf(((AEItemKey) inv.rawKeyAt(s)).getTag()) == config) got += take(p, member, offset + s, s, a, need - got, entry, flags);
        }
        return got;
    }

    private static long reserveTest(PlanScratch p, int member, int offset, KeyInventory<?> inv, KeyIngredient ing, int entry, long need, byte flags) {
        int size = inv.size();
        long got = 0;
        for (int s = 0; s < size && got < need; s++) {
            long a = inv.amountAt(s);
            if (a > 0 && ing.test(inv.rawKeyAt(s))) got += take(p, member, offset + s, s, a, need - got, entry, flags);
        }
        return got;
    }

    private static long take(PlanScratch p, int member, int index, int slot, long stored, long want, int entry, byte flags) {
        long free = stored - p.reserved(index);
        if (free <= 0) return 0;
        long t = free < want ? free : want;
        p.addReserved(index, t);
        p.log(member, slot, entry, t, flags);
        return t;
    }

    private static long sumArray(KeyInventory<?> inv, KeyIngredient ing, long stop) {
        Object o = ing;
        if (o instanceof AEItemKey k) return k.hasTag() ? sumKey(inv, k, stop) : sumUid(inv, k.uid, stop);
        if (o instanceof AEFluidKey k) return k.hasTag() ? sumKey(inv, k, stop) : sumUid(inv, k.uid, stop);
        if (o instanceof ItemTagIngredient t) return sumItemTag(inv, t.tag, stop);
        if (o instanceof FluidTagIngredient t) return sumFluidTag(inv, t.tag, stop);
        if (o instanceof CircuitIngredient c) return sumCircuit(inv, c.config, stop);
        return sumTest(inv, ing, stop);
    }

    private static long sumKey(KeyInventory<?> inv, AEKey key, long stop) {
        int size = inv.size();
        long sum = 0;
        for (int s = 0; s < size; s++) {
            long a = inv.amountAt(s);
            if (a > 0 && inv.rawKeyAt(s) == key) {
                sum = Keys.add(sum, a);
                if (sum >= stop) break;
            }
        }
        return sum;
    }

    private static long sumUid(KeyInventory<?> inv, int uid, long stop) {
        int size = inv.size();
        long sum = 0;
        for (int s = 0; s < size; s++) {
            long a = inv.amountAt(s);
            if (a > 0 && inv.uidAt(s) == uid) {
                sum = Keys.add(sum, a);
                if (sum >= stop) break;
            }
        }
        return sum;
    }

    private static long sumItemTag(KeyInventory<?> inv, TagKey<Item> tag, long stop) {
        int size = inv.size();
        long sum = 0;
        for (int s = 0; s < size; s++) {
            long a = inv.amountAt(s);
            if (a > 0 && ((AEItemKey) inv.rawKeyAt(s)).item.builtInRegistryHolder().is(tag)) {
                sum = Keys.add(sum, a);
                if (sum >= stop) break;
            }
        }
        return sum;
    }

    private static long sumFluidTag(KeyInventory<?> inv, TagKey<Fluid> tag, long stop) {
        int size = inv.size();
        long sum = 0;
        for (int s = 0; s < size; s++) {
            long a = inv.amountAt(s);
            if (a > 0 && ((AEFluidKey) inv.rawKeyAt(s)).fluid.is(tag)) {
                sum = Keys.add(sum, a);
                if (sum >= stop) break;
            }
        }
        return sum;
    }

    private static long sumCircuit(KeyInventory<?> inv, int config, long stop) {
        int size = inv.size();
        int uid = Circuits.uid();
        long sum = 0;
        for (int s = 0; s < size; s++) {
            long a = inv.amountAt(s);
            if (a > 0 && inv.uidAt(s) == uid && Circuits.configOf(((AEItemKey) inv.rawKeyAt(s)).getTag()) == config) {
                sum = Keys.add(sum, a);
                if (sum >= stop) break;
            }
        }
        return sum;
    }

    private static long sumTest(KeyInventory<?> inv, KeyIngredient ing, long stop) {
        int size = inv.size();
        long sum = 0;
        for (int s = 0; s < size; s++) {
            long a = inv.amountAt(s);
            if (a > 0 && ing.test(inv.rawKeyAt(s))) {
                sum = Keys.add(sum, a);
                if (sum >= stop) break;
            }
        }
        return sum;
    }

    public boolean planInputs(GTRecipe recipe, PlanScratch p, long scale, boolean rolled) {
        beginPlan(p);
        boolean emptyRecipe = recipe == GTRecipe.EMPTY;
        return planInputList(p, recipe.itemInputs, itemHandlers, itemStores, p.itemOffsets, AEKeyTypes.ITEMS, rolled ? p.itemNeed : null, (byte) 0, scale, emptyRecipe) &&
                planInputList(p, recipe.fluidInputs, fluidHandlers, fluidStores, p.fluidOffsets, AEKeyTypes.FLUIDS, rolled ? p.fluidNeed : null, PlanScratch.FLAG_FLUID, scale, emptyRecipe);
    }

    public boolean matchInputs(GTRecipe recipe) {
        var p = PlanScratch.acquire();
        try {
            return planInputs(recipe, p, recipe.scale, false);
        } finally {
            PlanScratch.release();
        }
    }

    public void rollInputs(GTRecipe recipe, PlanScratch p) {
        rollList(recipe, recipe.itemInputs, p.need(false, recipe.itemInputs.size()));
        rollList(recipe, recipe.fluidInputs, p.need(true, recipe.fluidInputs.size()));
    }

    static void rollList(GTRecipe recipe, ContentList list, long[] needs) {
        for (int i = 0; i < list.size(); i++) {
            needs[i] = ContentRoll.rolled(recipe, list, i, ContentRoll.RNG);
        }
    }

    public boolean commitFallible(PlanScratch p) {
        if (!commitPass(p, false)) return false;
        if (!versionsUnchanged(p) || !commitPass(p, true)) {
            rollback(p, false, itemHandlers.length - 1, fluidHandlers.length - 1);
            return false;
        }
        if (!versionsUnchanged(p)) {
            rollbackFallible(p);
            return false;
        }
        return true;
    }

    public void rollbackFallible(PlanScratch p) {
        rollback(p, false, itemHandlers.length - 1, fluidHandlers.length - 1);
        rollback(p, true, itemHandlers.length - 1, fluidHandlers.length - 1);
    }

    private boolean commitPass(PlanScratch p, boolean lossy) {
        var items = itemHandlers;
        var stores = itemStores;
        for (int h = 0; h < items.length; h++) {
            var m = items[h];
            if (m.isLossyRollback() != lossy || stores[h] != null || !hasLogs(p, h, false)) continue;
            if (!m.commitInput(p, h, AEKeyTypes.ITEMS)) {
                rollback(p, lossy, h - 1, -1);
                return false;
            }
        }
        var fluids = fluidHandlers;
        stores = fluidStores;
        for (int h = 0; h < fluids.length; h++) {
            var m = fluids[h];
            if (m.isLossyRollback() != lossy || stores[h] != null || !hasLogs(p, h, true)) continue;
            if (!m.commitInput(p, h, AEKeyTypes.FLUIDS)) {
                rollback(p, lossy, items.length - 1, h - 1);
                return false;
            }
        }
        return true;
    }

    private void rollback(PlanScratch p, boolean lossy, int lastItem, int lastFluid) {
        var items = itemHandlers;
        var stores = itemStores;
        for (int h = 0; h <= lastItem; h++) {
            var m = items[h];
            if (m.isLossyRollback() == lossy && stores[h] == null && hasLogs(p, h, false)) m.rollbackInput(p, h, AEKeyTypes.ITEMS);
        }
        var fluids = fluidHandlers;
        stores = fluidStores;
        for (int h = 0; h <= lastFluid; h++) {
            var m = fluids[h];
            if (m.isLossyRollback() == lossy && stores[h] == null && hasLogs(p, h, true)) m.rollbackInput(p, h, AEKeyTypes.FLUIDS);
        }
    }

    private static boolean hasLogs(PlanScratch p, int member, boolean fluid) {
        for (int i = 0; i < p.logSize; i++) {
            if (p.logMember[i] == member && ((p.logFlags[i] & PlanScratch.FLAG_FLUID) != 0) == fluid && (p.logFlags[i] & PlanScratch.FLAG_CONSUME) != 0) return true;
        }
        return false;
    }

    private boolean versionsUnchanged(PlanScratch p) {
        var stores = itemStores;
        var versions = p.itemVersions;
        for (int h = 0; h < stores.length; h++) {
            var inv = stores[h];
            if (inv != null && inv.version() != versions[h]) return false;
        }
        stores = fluidStores;
        versions = p.fluidVersions;
        for (int h = 0; h < stores.length; h++) {
            var inv = stores[h];
            if (inv != null && inv.version() != versions[h]) return false;
        }
        return true;
    }

    public void commitArrays(PlanScratch p) {
        int epoch = p.epoch();
        var items = itemStores;
        var fluids = fluidStores;
        int itemCount = items.length;
        int logSize = p.logSize;
        var logFlags = p.logFlags;
        var logMember = p.logMember;
        var logSlot = p.logSlot;
        var logAmount = p.logAmount;
        var touched = p.touched;
        for (int i = 0; i < logSize; i++) {
            byte flags = logFlags[i];
            if ((flags & PlanScratch.FLAG_CONSUME) == 0) continue;
            boolean fluid = (flags & PlanScratch.FLAG_FLUID) != 0;
            int h = logMember[i];
            var inv = fluid ? fluids[h] : items[h];
            if (inv == null) continue;
            inv.extractQuiet(logSlot[i], logAmount[i]);
            touched[fluid ? itemCount + h : h] = epoch;
        }
        for (int h = 0; h < itemCount; h++) {
            if (touched[h] == epoch) items[h].notifyChanged();
        }
        for (int h = 0; h < fluids.length; h++) {
            if (touched[itemCount + h] == epoch) fluids[h].notifyChanged();
        }
    }

    public boolean fitsOutputs(GTRecipe recipe, PlanScratch p, long scale) {
        return fitsOutputs(recipe, p, scale, true, true);
    }

    public boolean fitsOutputs(GTRecipe recipe, PlanScratch p, long scale, boolean checkItems, boolean checkFluids) {
        var items = recipe.itemOutputs;
        var fluids = recipe.fluidOutputs;
        beginPlan(p);
        if (checkItems && !items.isEmpty() && !isInfiniteItemCapacity && !planOutputList(p, items, itemHandlers, itemStores, p.itemOffsets, AEKeyTypes.ITEMS, scale)) return false;
        return !checkFluids || fluids.isEmpty() || isInfiniteFluidCapacity || planOutputList(p, fluids, fluidHandlers, fluidStores, p.fluidOffsets, AEKeyTypes.FLUIDS, scale);
    }

    public long outputParallelBound(GTRecipe recipe, PlanScratch p, boolean checkItems, boolean checkFluids) {
        long scale = recipe.scale;
        long bound = Long.MAX_VALUE;
        boolean exact = true;
        var items = recipe.itemOutputs;
        if (checkItems && !items.isEmpty() && !isInfiniteItemCapacity) {
            long b = outputListBound(p, items, itemStores, scale);
            if (b < 0) {
                exact = false;
                b = ~b;
            }
            if (b < bound) bound = b;
        }
        var fluids = recipe.fluidOutputs;
        if (checkFluids && !fluids.isEmpty() && !isInfiniteFluidCapacity) {
            long b = outputListBound(p, fluids, fluidStores, scale);
            if (b < 0) {
                exact = false;
                b = ~b;
            }
            if (b < bound) bound = b;
        }
        return exact ? bound : ~bound;
    }

    private static boolean activeOutput(ContentList list, int i) {
        return list.chance(i) != 0 && list.amount(i) > 0;
    }

    private static long outputListBound(PlanScratch p, ContentList list, KeyInventory<?>[] stores, long scale) {
        if (!list.hasActiveOutput()) return Long.MAX_VALUE;
        if (!list.distinctOutputKeys()) return ~Long.MAX_VALUE;
        int n = list.size();
        long[] cap = p.outLeft(n);
        Arrays.fill(cap, 0, n, 0L);
        boolean exact = true;
        for (var inv : stores) {
            if (inv == null) return ~Long.MAX_VALUE;
            int size = inv.size();
            boolean unique = inv.isUniqueKeys();
            int spill = 0;
            for (int i = 0; i < n; i++) {
                if (!activeOutput(list, i)) continue;
                var key = list.outputKey(i);
                long limit = inv.limitFor(key);
                if (limit <= 0) continue;
                long c = 0;
                long empty = 0;
                boolean hasSlot = false;
                for (int s = 0; s < size; s++) {
                    long stored = inv.amountAt(s);
                    if (stored > 0) {
                        if (inv.rawKeyAt(s) == key) {
                            hasSlot = true;
                            long space = limit - stored;
                            if (space > 0) c = saturatedAdd(c, space);
                        }
                    } else if (!unique && inv.acceptsEmpty(s, key)) {
                        empty++;
                    }
                }
                if (unique && !hasSlot) {
                    for (int s = 0; s < size; s++) {
                        if (inv.amountAt(s) <= 0 && inv.acceptsEmpty(s, key)) {
                            empty = 1;
                            break;
                        }
                    }
                }
                if (empty > 0) {
                    c = saturatedAdd(c, Keys.multiply(empty, limit));
                    spill++;
                }
                cap[i] = saturatedAdd(cap[i], c);
            }
            if (spill > 1) exact = false;
        }
        long bound = Long.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (!activeOutput(list, i)) continue;
            long c = cap[i];
            if (c == Long.MAX_VALUE) continue;
            long unit = Keys.multiply(list.amount(i), scale);
            long b = unit == Long.MAX_VALUE ? 0 : c / unit;
            if (b < bound) bound = b;
        }
        return exact ? bound : ~bound;
    }

    private static long saturatedAdd(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r;
    }

    private static boolean planOutputList(PlanScratch p, ContentList list, IRecipeHandler[] members, KeyInventory<?>[] stores, int[] offsets, AEKeyType type, long scale) {
        int n = list.size();
        long[] left = p.outLeft(n);
        long total = 0;
        for (int i = 0; i < n; i++) {
            long a = list.chance(i) == 0 ? 0 : list.effective(i, scale);
            left[i] = a;
            total |= a;
        }
        if (total == 0) return true;
        for (int h = 0; h < members.length; h++) {
            var inv = stores[h];
            boolean remaining = false;
            for (int i = 0; i < n; i++) {
                long l = left[i];
                if (l <= 0) continue;
                var key = list.outputKey(i);
                l -= inv != null ? reserveOutputArray(p, offsets[h], inv, list, key, i, l) : members[h].reserveOutput(p, h, type, i, key, l);
                left[i] = l;
                if (l > 0) remaining = true;
            }
            if (!remaining) return true;
        }
        for (int i = 0; i < n; i++) {
            if (left[i] > 0) return false;
        }
        return true;
    }

    private static long reserveOutputArray(PlanScratch p, int offset, KeyInventory<?> inv, ContentList list, AEKey key, int entry, long amount) {
        long limit = inv.limitFor(key);
        if (limit <= 0) return 0;
        int size = inv.size();
        boolean unique = inv.isUniqueKeys();
        long left = amount;
        boolean hasSlot = false;
        for (int s = 0; s < size && left > 0; s++) {
            int idx = offset + s;
            long stored = inv.amountAt(s);
            boolean same;
            if (stored > 0) {
                same = inv.rawKeyAt(s) == key;
            } else {
                int c = p.claim(idx);
                same = c > 0 && list.outputKey(c - 1) == key;
            }
            if (!same) continue;
            hasSlot = true;
            long space = limit - stored - p.reserved(idx);
            if (space <= 0) continue;
            long t = left < space ? left : space;
            p.addReserved(idx, t);
            left -= t;
        }
        if (left > 0 && !(hasSlot && unique)) {
            for (int s = 0; s < size && left > 0; s++) {
                int idx = offset + s;
                if (inv.amountAt(s) > 0 || p.claim(idx) > 0 || !inv.acceptsEmpty(s, key)) continue;
                long t = left < limit ? left : limit;
                p.setClaim(idx, entry + 1);
                p.addReserved(idx, t);
                left -= t;
                if (unique) break;
            }
        }
        return amount - left;
    }

    public boolean insertOutputs(ContentList list, long[] amounts, boolean fluid) {
        var members = fluid ? fluidHandlers : itemHandlers;
        var stores = fluid ? fluidStores : itemStores;
        var type = fluid ? AEKeyTypes.FLUIDS : AEKeyTypes.ITEMS;
        int n = list.size();
        for (int h = 0; h < members.length; h++) {
            var inv = stores[h];
            var m = members[h];
            boolean inserted = false;
            boolean remaining = false;
            for (int i = 0; i < n; i++) {
                long left = amounts[i];
                if (left <= 0) continue;
                var key = list.outputKey(i);
                long done = inv != null ? insertInto(inv, key, left) : m.insertOutput(type, key, left);
                if (done > 0) inserted = true;
                left -= done;
                amounts[i] = left;
                if (left > 0) remaining = true;
            }
            if (inserted && inv != null) inv.notifyChanged();
            if (!remaining) return true;
        }
        for (int i = 0; i < n; i++) {
            if (amounts[i] > 0) return false;
        }
        return true;
    }

    private static long insertInto(KeyInventory<?> inv, AEKey key, long amount) {
        long limit = inv.limitFor(key);
        if (limit <= 0) return 0;
        int size = inv.size();
        boolean unique = inv.isUniqueKeys();
        long left = amount;
        boolean hasSlot = false;
        for (int s = 0; s < size && left > 0; s++) {
            long stored = inv.amountAt(s);
            if (stored > 0 && inv.rawKeyAt(s) == key) {
                hasSlot = true;
                long space = limit - stored;
                if (space <= 0) continue;
                long t = left < space ? left : space;
                inv.insertQuiet(s, key, t);
                left -= t;
            }
        }
        if (left > 0 && !(hasSlot && unique)) {
            for (int s = 0; s < size && left > 0; s++) {
                if (inv.amountAt(s) > 0 || !inv.acceptsEmpty(s, key)) continue;
                long t = left < limit ? left : limit;
                inv.insertQuiet(s, key, t);
                left -= t;
                if (unique) break;
            }
        }
        return amount - left;
    }

    public long inputParallel(GTRecipe recipe, long limit) {
        long scale = recipe.scale;
        long par = sumParallel(recipe.itemInputs, itemHandlers, itemStores, AEKeyTypes.ITEMS, scale, limit);
        if (par == 0) return 0;
        return sumParallel(recipe.fluidInputs, fluidHandlers, fluidStores, AEKeyTypes.FLUIDS, scale, par);
    }

    private static long sumParallel(ContentList list, IRecipeHandler[] members, KeyInventory<?>[] stores, AEKeyType type, long scale, long par) {
        if (list.hasOverlap()) return overlapParallel(list, members, stores, type, scale, par);
        int n = list.size();
        for (int i = 0; i < n; i++) {
            par = entryParallel(list, i, members, stores, type, scale, par);
            if (par == 0) return 0;
        }
        return par;
    }

    private static long overlapParallel(ContentList list, IRecipeHandler[] members, KeyInventory<?>[] stores, AEKeyType type, long scale, long par) {
        int separate = list.size() - list.overlapCount();
        for (int j = 0; j < separate; j++) {
            par = entryParallel(list, list.separateEntry(j), members, stores, type, scale, par);
            if (par == 0) return 0;
        }
        var p = PlanScratch.acquire();
        try {
            return p.overlap().bound(list, members, stores, type, scale, par);
        } finally {
            PlanScratch.release();
        }
    }

    private static long entryParallel(ContentList list, int i, IRecipeHandler[] members, KeyInventory<?>[] stores, AEKeyType type, long scale, long par) {
        boolean consume = list.isConsumable(i);
        long need = consume ? list.effective(i, scale) : list.amount(i);
        if (need <= 0) return par;
        var ing = list.ingredient(i);
        long enough = consume ? Keys.multiply(need, par) : need;
        long avail = 0;
        for (int h = 0, memberCount = members.length; h < memberCount && avail < enough; h++) {
            var m = members[h];
            if (consume && m.isNotConsumable()) continue;
            var inv = stores[h];
            if (inv == null) {
                avail = Keys.add(avail, m.available(type, ing));
            } else if (!consume && m.isPresenceOnly()) {
                if (sumArray(inv, ing, 1) > 0) avail = Long.MAX_VALUE;
            } else {
                avail = Keys.add(avail, sumArray(inv, ing, enough - avail));
            }
        }
        if (avail < need) return 0;
        if (consume) {
            long q = avail / need;
            if (q < par) par = q;
        }
        return par;
    }

    public boolean consume(KeyIngredient ing, long amount, boolean simulate) {
        if (amount <= 0) return true;
        var p = PlanScratch.acquire();
        try {
            beginPlan(p);
            boolean fluid = ing.isFluid();
            var members = fluid ? fluidHandlers : itemHandlers;
            var stores = fluid ? fluidStores : itemStores;
            var offsets = fluid ? p.fluidOffsets : p.itemOffsets;
            var type = fluid ? AEKeyTypes.FLUIDS : AEKeyTypes.ITEMS;
            byte flags = fluid ? (byte) (PlanScratch.FLAG_CONSUME | PlanScratch.FLAG_FLUID) : PlanScratch.FLAG_CONSUME;
            long need = amount;
            for (int h = 0; h < members.length && need > 0; h++) {
                var m = members[h];
                if (m.isNotConsumable() || m.isOnlyRecipe()) continue;
                var inv = stores[h];
                need -= inv != null ? reserveArray(p, h, offsets[h], inv, ing, 0, need, flags) : m.reserveInput(p, h, type, 0, ing, need, true);
            }
            boolean ok = need <= 0;
            if (ok && !simulate) {
                ok = commitFallible(p);
                if (ok) commitArrays(p);
            }
            return ok;
        } finally {
            PlanScratch.release();
        }
    }

    public long count(KeyIngredient ing, boolean consumable) {
        boolean fluid = ing.isFluid();
        var members = fluid ? fluidHandlers : itemHandlers;
        var stores = fluid ? fluidStores : itemStores;
        var type = fluid ? AEKeyTypes.FLUIDS : AEKeyTypes.ITEMS;
        long total = 0;
        for (int h = 0; h < members.length; h++) {
            var m = members[h];
            if (consumable && m.isNotConsumable()) continue;
            var inv = stores[h];
            total = Keys.add(total, inv != null ? sumArray(inv, ing, Long.MAX_VALUE) : m.available(type, ing));
        }
        return total;
    }

    public boolean output(AEKey key, long amount, boolean simulate) {
        if (amount <= 0) return true;
        boolean fluid = key instanceof AEFluidKey;
        var members = fluid ? fluidHandlers : itemHandlers;
        var stores = fluid ? fluidStores : itemStores;
        var type = fluid ? AEKeyTypes.FLUIDS : AEKeyTypes.ITEMS;
        if (simulate) {
            if (fluid ? isInfiniteFluidCapacity : isInfiniteItemCapacity) return true;
            var p = PlanScratch.acquire();
            try {
                beginPlan(p);
                long left = amount;
                for (int h = 0; h < members.length && left > 0; h++) {
                    var inv = stores[h];
                    left -= inv != null ? reserveOutputSingle(inv, key, left) : members[h].reserveOutput(p, h, type, 0, key, left);
                }
                return left <= 0;
            } finally {
                PlanScratch.release();
            }
        }
        long left = amount;
        for (int h = 0; h < members.length && left > 0; h++) {
            var inv = stores[h];
            if (inv != null) {
                long n = insertInto(inv, key, left);
                if (n > 0) inv.notifyChanged();
                left -= n;
            } else {
                left -= members[h].insertOutput(type, key, left);
            }
        }
        return left <= 0;
    }

    private static long reserveOutputSingle(KeyInventory<?> inv, AEKey key, long amount) {
        long limit = inv.limitFor(key);
        if (limit <= 0) return 0;
        int size = inv.size();
        boolean unique = inv.isUniqueKeys();
        long left = amount;
        boolean hasSlot = false;
        for (int s = 0; s < size && left > 0; s++) {
            long stored = inv.amountAt(s);
            if (stored > 0 && inv.rawKeyAt(s) == key) {
                hasSlot = true;
                long space = limit - stored;
                if (space > 0) left -= Math.min(left, space);
            }
        }
        if (left > 0 && !(hasSlot && unique)) {
            for (int s = 0; s < size && left > 0; s++) {
                if (inv.amountAt(s) > 0 || !inv.acceptsEmpty(s, key)) continue;
                left -= Math.min(left, limit);
                if (unique) break;
            }
        }
        return amount - left;
    }

    private static KeyIngredient baseItem(ItemLike item) {
        return KeyIngredient.exact(AEItemKey.of(item, null));
    }

    public boolean inputItem(ItemLike item, long amount) {
        var ing = baseItem(item);
        return consume(ing, amount, true) && consume(ing, amount, false);
    }

    public boolean inputItem(AEItemKey key, long amount) {
        var ing = KeyIngredient.exact(key);
        return consume(ing, amount, true) && consume(ing, amount, false);
    }

    public boolean inputFluid(Fluid fluid, long amount) {
        var ing = KeyIngredient.fluid(fluid);
        return consume(ing, amount, true) && consume(ing, amount, false);
    }

    public boolean inputFluid(AEFluidKey key, long amount) {
        var ing = KeyIngredient.exact(key);
        return consume(ing, amount, true) && consume(ing, amount, false);
    }

    public boolean simulateOutputItem(ItemLike item, long amount) {
        return output(AEItemKey.of(item), amount, true);
    }

    public boolean outputItem(ItemLike item, long amount) {
        return output(AEItemKey.of(item), amount, false);
    }

    public boolean simulateOutputFluid(Fluid fluid, long amount) {
        return output(AEFluidKey.of(fluid), amount, true);
    }

    public boolean outputFluid(Fluid fluid, long amount) {
        return output(AEFluidKey.of(fluid), amount, false);
    }

    public boolean matchItem(ItemLike item) {
        return matchItem(item, 1);
    }

    public boolean matchItem(ItemLike item, long amount) {
        return count(baseItem(item), false) >= amount;
    }

    public boolean matchKey(AEKey key, long amount) {
        return count(KeyIngredient.exact(key), false) >= amount;
    }

    public boolean matchFluid(Fluid fluid) {
        return matchFluid(fluid, 1);
    }

    public boolean matchFluid(Fluid fluid, long amount) {
        return count(KeyIngredient.fluid(fluid), false) >= amount;
    }

    public boolean matchCircuit(int configuration) {
        return getCircuit(false, configuration) >= 0;
    }

    public int getCircuit(boolean sum) {
        return Math.max(0, getCircuit(sum, -1));
    }

    private int getCircuit(boolean sum, int wanted) {
        int circuit = 0;
        boolean found = false;
        int circuitUid = Circuits.uid();
        var members = itemHandlers;
        var stores = itemStores;
        for (int h = 0; h < members.length; h++) {
            if (!members[h].isNotConsumable()) continue;
            var inv = stores[h];
            if (inv == null) continue;
            int size = inv.size();
            for (int s = 0; s < size; s++) {
                if (inv.amountAt(s) <= 0 || inv.uidAt(s) != circuitUid) continue;
                int c = Circuits.configOf(((AEItemKey) inv.rawKeyAt(s)).getTag());
                if (wanted >= 0) {
                    if (c == wanted) return c;
                    continue;
                }
                if (c > 0) {
                    found = true;
                    circuit += c;
                    if (!sum) return circuit;
                }
            }
        }
        return wanted >= 0 ? -1 : found ? circuit : 0;
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
        for (int i = 0; i < items.length; i++) {
            amounts[i] = Keys.add(amounts[i], count(baseItem(items[i]), consumable));
        }
    }

    public void getFluidAmount(boolean consumable, Fluid[] fluids, long[] amounts) {
        for (int i = 0; i < fluids.length; i++) {
            amounts[i] = Keys.add(amounts[i], count(KeyIngredient.fluid(fluids[i]), consumable));
        }
    }

    public boolean forEachKey(AEKeyType type, boolean consumable, IRecipeHandler.KeyVisitor visitor) {
        var members = type == AEKeyTypes.FLUIDS ? fluidHandlers : itemHandlers;
        for (var m : members) {
            if (consumable && m.isNotConsumable()) continue;
            if (m.forEachKey(type, visitor)) return true;
        }
        return false;
    }
}
