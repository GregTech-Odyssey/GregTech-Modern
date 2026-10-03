package com.gregtechceu.gtceu.api.recipe.handler;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IWorkableMultiController;
import com.gregtechceu.gtceu.api.machine.trait.IRecipeHandlerTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableContentHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Circuits;
import com.gregtechceu.gtceu.api.recipe.content.ContentList;
import com.gregtechceu.gtceu.api.recipe.content.ContentRoll;
import com.gregtechceu.gtceu.api.recipe.content.KeyIngredient;
import com.gregtechceu.gtceu.api.recipe.info.FluidRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.ItemRecipeInfo;
import com.gregtechceu.gtceu.api.recipe.info.RecipeInfo;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.hooks.IUnique;
import com.gto.recipesearch.IntLongMap;
import org.jetbrains.annotations.NotNull;

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
                if (handler.isInfiniteCapacity(AEKeyType.items())) isInfiniteItemCapacity = true;
                items.add(handler);
            }
            if (handler.handlesFluids()) {
                if (handler.isInfiniteCapacity(AEKeyType.fluids())) isInfiniteFluidCapacity = true;
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

    private void beginPlan(PlanScratch p, ContentList items, ContentList fluids) {
        var ih = itemHandlers;
        var fh = fluidHandlers;
        p.ensureMembers(ih.length, fh.length);
        int slots = 0;
        for (int h = 0; h < ih.length; h++) {
            var inv = ih[h].storage(AEKeyType.items());
            p.itemStores[h] = inv;
            p.itemOffsets[h] = slots;
            if (inv != null) {
                p.itemVersions[h] = inv.version();
                slots += inv.size();
            }
        }
        for (int h = 0; h < fh.length; h++) {
            var inv = fh[h].storage(AEKeyType.fluids());
            p.fluidStores[h] = inv;
            p.fluidOffsets[h] = slots;
            if (inv != null) {
                p.fluidVersions[h] = inv.version();
                slots += inv.size();
            }
        }
        p.begin(slots);
    }

    private boolean planInputList(GTRecipe recipe, PlanScratch p, ContentList list, boolean fluid, long scale, boolean rolled) {
        int n = list.size();
        if (n == 0) return true;
        var members = fluid ? fluidHandlers : itemHandlers;
        var stores = fluid ? p.fluidStores : p.itemStores;
        var offsets = fluid ? p.fluidOffsets : p.itemOffsets;
        var type = fluid ? AEKeyType.fluids() : AEKeyType.items();
        long[] needs = rolled ? (fluid ? p.fluidNeed : p.itemNeed) : null;
        boolean emptyRecipe = recipe == GTRecipe.EMPTY;
        int[] order = list.planOrder();
        for (int o = 0; o < n; o++) {
            int i = order[o];
            boolean consume = list.isConsumable(i);
            long need = consume ? (needs != null ? needs[i] : list.effective(i, scale)) : list.amount(i);
            if (need <= 0) continue;
            var ing = list.ingredient(i);
            for (int h = 0; h < members.length && need > 0; h++) {
                var m = members[h];
                if (consume && m.isNotConsumable()) continue;
                if (emptyRecipe && m.isOnlyRecipe()) continue;
                var inv = stores[h];
                if (inv != null) {
                    if (!consume && m.isPresenceOnly()) {
                        if (containsMatch(inv, ing)) need = 0;
                    } else {
                        need -= reserveArray(p, h, offsets[h], inv, ing, i, need, consume, fluid);
                    }
                } else {
                    need -= m.reserveInput(p, h, type, i, ing, need, consume);
                }
            }
            if (need > 0) return false;
        }
        return true;
    }

    private static boolean containsMatch(KeyInventory<?> inv, KeyIngredient ing) {
        for (int s = 0, size = inv.size(); s < size; s++) {
            if (inv.amountAt(s) > 0 && ing.test(inv.uidAt(s), inv.rawKeyAt(s))) return true;
        }
        return false;
    }

    private static long reserveArray(PlanScratch p, int member, int offset, KeyInventory<?> inv, KeyIngredient ing, int entry, long need, boolean consume, boolean fluid) {
        int size = inv.size();
        long got = 0;
        byte flags = (byte) ((consume ? PlanScratch.FLAG_CONSUME : 0) | (fluid ? PlanScratch.FLAG_FLUID : 0));
        switch (ing.kind) {
            case KeyIngredient.EXACT -> {
                var key = ing.key();
                for (int s = 0; s < size && got < need; s++) {
                    long a = inv.amountAt(s);
                    if (a > 0 && inv.rawKeyAt(s) == key) got += take(p, member, offset + s, s, a, need - got, entry, flags);
                }
            }
            case KeyIngredient.BASE -> {
                int uid = ing.uid();
                for (int s = 0; s < size && got < need; s++) {
                    long a = inv.amountAt(s);
                    if (a > 0 && inv.uidAt(s) == uid) got += take(p, member, offset + s, s, a, need - got, entry, flags);
                }
            }
            default -> {
                for (int s = 0; s < size && got < need; s++) {
                    long a = inv.amountAt(s);
                    if (a > 0 && ing.test(inv.uidAt(s), inv.rawKeyAt(s))) got += take(p, member, offset + s, s, a, need - got, entry, flags);
                }
            }
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

    public boolean planInputs(GTRecipe recipe, PlanScratch p, long scale, boolean rolled) {
        beginPlan(p, recipe.itemInputs, recipe.fluidInputs);
        return planInputList(recipe, p, recipe.itemInputs, false, scale, rolled) && planInputList(recipe, p, recipe.fluidInputs, true, scale, rolled);
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
        for (int h = 0; h < itemHandlers.length; h++) {
            var m = itemHandlers[h];
            if (m.isLossyRollback() != lossy || p.itemStores[h] != null || !hasLogs(p, h, false)) continue;
            if (!m.commitInput(p, h, AEKeyType.items())) {
                rollback(p, lossy, h - 1, -1);
                return false;
            }
        }
        for (int h = 0; h < fluidHandlers.length; h++) {
            var m = fluidHandlers[h];
            if (m.isLossyRollback() != lossy || p.fluidStores[h] != null || !hasLogs(p, h, true)) continue;
            if (!m.commitInput(p, h, AEKeyType.fluids())) {
                rollback(p, lossy, itemHandlers.length - 1, h - 1);
                return false;
            }
        }
        return true;
    }

    private void rollback(PlanScratch p, boolean lossy, int lastItem, int lastFluid) {
        for (int h = 0; h <= lastItem; h++) {
            var m = itemHandlers[h];
            if (m.isLossyRollback() == lossy && p.itemStores[h] == null && hasLogs(p, h, false)) m.rollbackInput(p, h, AEKeyType.items());
        }
        for (int h = 0; h <= lastFluid; h++) {
            var m = fluidHandlers[h];
            if (m.isLossyRollback() == lossy && p.fluidStores[h] == null && hasLogs(p, h, true)) m.rollbackInput(p, h, AEKeyType.fluids());
        }
    }

    private static boolean hasLogs(PlanScratch p, int member, boolean fluid) {
        for (int i = 0; i < p.logSize; i++) {
            if (p.logMember[i] == member && ((p.logFlags[i] & PlanScratch.FLAG_FLUID) != 0) == fluid && (p.logFlags[i] & PlanScratch.FLAG_CONSUME) != 0) return true;
        }
        return false;
    }

    private boolean versionsUnchanged(PlanScratch p) {
        for (int h = 0; h < itemHandlers.length; h++) {
            var inv = p.itemStores[h];
            if (inv != null && inv.version() != p.itemVersions[h]) return false;
        }
        for (int h = 0; h < fluidHandlers.length; h++) {
            var inv = p.fluidStores[h];
            if (inv != null && inv.version() != p.fluidVersions[h]) return false;
        }
        return true;
    }

    public void commitArrays(PlanScratch p) {
        int epoch = p.epoch();
        int itemCount = itemHandlers.length;
        for (int i = 0; i < p.logSize; i++) {
            byte flags = p.logFlags[i];
            if ((flags & PlanScratch.FLAG_CONSUME) == 0) continue;
            boolean fluid = (flags & PlanScratch.FLAG_FLUID) != 0;
            int h = p.logMember[i];
            var inv = fluid ? p.fluidStores[h] : p.itemStores[h];
            if (inv == null) continue;
            inv.extractQuiet(p.logSlot[i], p.logAmount[i]);
            p.touched[fluid ? itemCount + h : h] = epoch;
        }
        for (int h = 0; h < itemCount; h++) {
            if (p.touched[h] == epoch) p.itemStores[h].notifyChanged();
        }
        for (int h = 0; h < fluidHandlers.length; h++) {
            if (p.touched[itemCount + h] == epoch) p.fluidStores[h].notifyChanged();
        }
    }

    public boolean fitsOutputs(GTRecipe recipe, PlanScratch p, long scale) {
        return fitsOutputs(recipe, p, scale, true, true);
    }

    public boolean fitsOutputs(GTRecipe recipe, PlanScratch p, long scale, boolean checkItems, boolean checkFluids) {
        var items = recipe.itemOutputs;
        var fluids = recipe.fluidOutputs;
        beginPlan(p, items, fluids);
        if (checkItems && !items.isEmpty() && !isInfiniteItemCapacity && !planOutputList(p, items, false, scale)) return false;
        return !checkFluids || fluids.isEmpty() || isInfiniteFluidCapacity || planOutputList(p, fluids, true, scale);
    }

    public long outputParallelBound(GTRecipe recipe, PlanScratch p, boolean checkItems, boolean checkFluids) {
        long scale = recipe.scale;
        if (scale < 1) return ~Long.MAX_VALUE;
        long bound = Long.MAX_VALUE;
        boolean exact = true;
        var items = recipe.itemOutputs;
        if (checkItems && !items.isEmpty() && !isInfiniteItemCapacity) {
            long b = outputListBound(p, items, itemHandlers, AEKeyType.items(), scale);
            if (b < 0) {
                exact = false;
                b = ~b;
            }
            if (b < bound) bound = b;
        }
        var fluids = recipe.fluidOutputs;
        if (checkFluids && !fluids.isEmpty() && !isInfiniteFluidCapacity) {
            long b = outputListBound(p, fluids, fluidHandlers, AEKeyType.fluids(), scale);
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

    private static long outputListBound(PlanScratch p, ContentList list, IRecipeHandler[] members, AEKeyType type, long scale) {
        int n = list.size();
        int active = 0;
        for (int i = 0; i < n; i++) {
            if (!activeOutput(list, i)) continue;
            active++;
            var key = list.ingredient(i).key();
            if (key == null) return ~Long.MAX_VALUE;
            for (int j = 0; j < i; j++) {
                if (activeOutput(list, j) && list.ingredient(j).key() == key) return ~Long.MAX_VALUE;
            }
        }
        if (active == 0) return Long.MAX_VALUE;
        long[] cap = p.outLeft(n);
        for (int i = 0; i < n; i++) cap[i] = 0;
        boolean exact = true;
        for (var m : members) {
            var inv = m.storage(type);
            if (inv == null) return ~Long.MAX_VALUE;
            int size = inv.size();
            boolean unique = inv.isUniqueKeys();
            int spill = 0;
            for (int i = 0; i < n; i++) {
                if (!activeOutput(list, i)) continue;
                var key = list.ingredient(i).key();
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

    private boolean planOutputList(PlanScratch p, ContentList list, boolean fluid, long scale) {
        var members = fluid ? fluidHandlers : itemHandlers;
        var stores = fluid ? p.fluidStores : p.itemStores;
        var offsets = fluid ? p.fluidOffsets : p.itemOffsets;
        var type = fluid ? AEKeyType.fluids() : AEKeyType.items();
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
        if (left > 0 && !(hasSlot && inv.isUniqueKeys())) {
            for (int s = 0; s < size && left > 0; s++) {
                int idx = offset + s;
                if (inv.amountAt(s) > 0 || p.claim(idx) > 0 || !inv.acceptsEmpty(s, key)) continue;
                long t = left < limit ? left : limit;
                p.setClaim(idx, entry + 1);
                p.addReserved(idx, t);
                left -= t;
                if (inv.isUniqueKeys()) break;
            }
        }
        return amount - left;
    }

    public boolean insertOutputs(ContentList list, long[] amounts, boolean fluid) {
        var members = fluid ? fluidHandlers : itemHandlers;
        var type = fluid ? AEKeyType.fluids() : AEKeyType.items();
        int n = list.size();
        for (var m : members) {
            var inv = m.storage(type);
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
        long left = amount;
        boolean hasSlot = false;
        for (int s = 0; s < size && left > 0; s++) {
            if (inv.amountAt(s) > 0 && inv.rawKeyAt(s) == key) {
                hasSlot = true;
                long space = limit - inv.amountAt(s);
                if (space <= 0) continue;
                long t = left < space ? left : space;
                inv.insertQuiet(s, key, t);
                left -= t;
            }
        }
        if (left > 0 && !(hasSlot && inv.isUniqueKeys())) {
            for (int s = 0; s < size && left > 0; s++) {
                if (inv.amountAt(s) > 0 || !inv.acceptsEmpty(s, key)) continue;
                long t = left < limit ? left : limit;
                inv.insertQuiet(s, key, t);
                left -= t;
                if (inv.isUniqueKeys()) break;
            }
        }
        return amount - left;
    }

    public long inputParallel(GTRecipe recipe, long limit) {
        var p = PlanScratch.acquire();
        try {
            return inputParallel(recipe, limit, p);
        } finally {
            PlanScratch.release();
        }
    }

    private long inputParallel(GTRecipe recipe, long limit, PlanScratch p) {
        beginPlan(p, recipe.itemInputs, recipe.fluidInputs);
        p.overlap = false;
        long par = limit;
        par = sumParallel(recipe, p, recipe.itemInputs, false, par);
        if (par == 0) return 0;
        par = sumParallel(recipe, p, recipe.fluidInputs, true, par);
        if (par == 0) return 0;
        if (!p.overlap) return par;
        if (planInputs(recipe, p, Keys.multiply(recipe.scale, par), false)) return par;
        long low = 0, high = par;
        while (low + 1 < high) {
            long mid = low + (high - low) / 2;
            if (planInputs(recipe, p, Keys.multiply(recipe.scale, mid), false)) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private long sumParallel(GTRecipe recipe, PlanScratch p, ContentList list, boolean fluid, long par) {
        int n = list.size();
        var members = fluid ? fluidHandlers : itemHandlers;
        var stores = fluid ? p.fluidStores : p.itemStores;
        var offsets = fluid ? p.fluidOffsets : p.itemOffsets;
        var type = fluid ? AEKeyType.fluids() : AEKeyType.items();
        for (int i = 0; i < n; i++) {
            boolean consume = list.isConsumable(i);
            long need = consume ? list.effective(i, recipe.scale) : list.amount(i);
            if (need <= 0) continue;
            var ing = list.ingredient(i);
            long avail = 0;
            for (int h = 0; h < members.length; h++) {
                var m = members[h];
                if (consume && m.isNotConsumable()) continue;
                var inv = stores[h];
                if (inv != null) {
                    if (!consume && m.isPresenceOnly()) {
                        if (containsMatch(inv, ing)) avail = Long.MAX_VALUE;
                        continue;
                    }
                    avail = Keys.add(avail, sumArray(p, offsets[h], inv, ing, (fluid ? 0x40000000 : 0) | (i + 1)));
                } else {
                    avail = Keys.add(avail, m.available(type, ing));
                }
            }
            if (avail < need) return 0;
            if (consume) {
                long q = avail / need;
                if (q < par) par = q;
            }
        }
        return par;
    }

    private static long sumArray(PlanScratch p, int offset, KeyInventory<?> inv, KeyIngredient ing, int mark) {
        int size = inv.size();
        long sum = 0;
        for (int s = 0; s < size; s++) {
            long a = inv.amountAt(s);
            if (a <= 0 || !ing.test(inv.uidAt(s), inv.rawKeyAt(s))) continue;
            int idx = offset + s;
            int c = p.claim(idx);
            if (c != 0 && c != mark) p.overlap = true;
            p.setClaim(idx, mark);
            sum = Keys.add(sum, a);
        }
        return sum;
    }

    public boolean consume(KeyIngredient ing, long amount, boolean simulate) {
        if (amount <= 0) return true;
        var p = PlanScratch.acquire();
        try {
            boolean fluid = ing.isFluid();
            beginPlan(p, ContentList.EMPTY, ContentList.EMPTY);
            var members = fluid ? fluidHandlers : itemHandlers;
            var stores = fluid ? p.fluidStores : p.itemStores;
            var offsets = fluid ? p.fluidOffsets : p.itemOffsets;
            var type = fluid ? AEKeyType.fluids() : AEKeyType.items();
            long need = amount;
            for (int h = 0; h < members.length && need > 0; h++) {
                var m = members[h];
                if (m.isNotConsumable() || m.isOnlyRecipe()) continue;
                var inv = stores[h];
                need -= inv != null ? reserveArray(p, h, offsets[h], inv, ing, 0, need, true, fluid) : m.reserveInput(p, h, type, 0, ing, need, true);
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
        var type = fluid ? AEKeyType.fluids() : AEKeyType.items();
        long total = 0;
        for (var m : members) {
            if (consumable && m.isNotConsumable()) continue;
            var inv = m.storage(type);
            if (inv != null) {
                int size = inv.size();
                for (int s = 0; s < size; s++) {
                    long a = inv.amountAt(s);
                    if (a > 0 && ing.test(inv.uidAt(s), inv.rawKeyAt(s))) total = Keys.add(total, a);
                }
            } else {
                total = Keys.add(total, m.available(type, ing));
            }
        }
        return total;
    }

    public boolean output(AEKey key, long amount, boolean simulate) {
        if (amount <= 0) return true;
        boolean fluid = key.getType() == AEKeyType.fluids();
        var type = fluid ? AEKeyType.fluids() : AEKeyType.items();
        var members = fluid ? fluidHandlers : itemHandlers;
        if (simulate) {
            if (fluid ? isInfiniteFluidCapacity : isInfiniteItemCapacity) return true;
            var p = PlanScratch.acquire();
            try {
                beginPlan(p, ContentList.EMPTY, ContentList.EMPTY);
                var stores = fluid ? p.fluidStores : p.itemStores;
                var offsets = fluid ? p.fluidOffsets : p.itemOffsets;
                long left = amount;
                for (int h = 0; h < members.length && left > 0; h++) {
                    var inv = stores[h];
                    left -= inv != null ? reserveOutputSingle(p, offsets[h], inv, key, left) : members[h].reserveOutput(p, h, type, 0, key, left);
                }
                return left <= 0;
            } finally {
                PlanScratch.release();
            }
        }
        long left = amount;
        for (int h = 0; h < members.length && left > 0; h++) {
            var m = members[h];
            var inv = m.storage(type);
            if (inv != null) {
                long n = insertInto(inv, key, left);
                if (n > 0) inv.notifyChanged();
                left -= n;
            } else {
                left -= m.insertOutput(type, key, left);
            }
        }
        return left <= 0;
    }

    private static long reserveOutputSingle(PlanScratch p, int offset, KeyInventory<?> inv, AEKey key, long amount) {
        long limit = inv.limitFor(key);
        if (limit <= 0) return 0;
        long left = amount;
        int size = inv.size();
        boolean hasSlot = false;
        for (int s = 0; s < size && left > 0; s++) {
            long stored = inv.amountAt(s);
            if (stored > 0 && inv.rawKeyAt(s) == key) {
                hasSlot = true;
                long space = limit - stored;
                if (space > 0) left -= Math.min(left, space);
            }
        }
        if (left > 0 && !(hasSlot && inv.isUniqueKeys())) {
            for (int s = 0; s < size && left > 0; s++) {
                if (inv.amountAt(s) > 0 || !inv.acceptsEmpty(s, key)) continue;
                left -= Math.min(left, limit);
                if (inv.isUniqueKeys()) break;
            }
        }
        return amount - left;
    }

    public boolean inputItem(ItemLike item, long amount) {
        return consume(KeyIngredient.item(item), amount, true) && consume(KeyIngredient.item(item), amount, false);
    }

    public boolean inputItem(AEItemKey key, long amount) {
        var ing = KeyIngredient.exact(key);
        return consume(ing, amount, true) && consume(ing, amount, false);
    }

    public boolean inputFluid(Fluid fluid, long amount) {
        return consume(KeyIngredient.fluid(fluid), amount, true) && consume(KeyIngredient.fluid(fluid), amount, false);
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
        return count(KeyIngredient.item(item), false) >= amount;
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
        int circuitUid = IUnique.getUid(Circuits.item());
        for (var h : itemHandlers) {
            if (!h.isNotConsumable()) continue;
            var inv = h.storage(AEKeyType.items());
            if (inv == null) continue;
            for (int s = 0; s < inv.size(); s++) {
                if (inv.amountAt(s) <= 0 || inv.uidAt(s) != circuitUid) continue;
                int c = Circuits.configOf((AEItemKey) inv.rawKeyAt(s));
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
            amounts[i] = Keys.add(amounts[i], count(KeyIngredient.item(items[i]), consumable));
        }
    }

    public void getFluidAmount(boolean consumable, Fluid[] fluids, long[] amounts) {
        for (int i = 0; i < fluids.length; i++) {
            amounts[i] = Keys.add(amounts[i], count(KeyIngredient.fluid(fluids[i]), consumable));
        }
    }

    public boolean forEachKey(AEKeyType type, boolean consumable, IRecipeHandler.KeyVisitor visitor) {
        var members = type == AEKeyType.fluids() ? fluidHandlers : itemHandlers;
        for (var m : members) {
            if (consumable && m.isNotConsumable()) continue;
            if (m.forEachKey(type, visitor)) return true;
        }
        return false;
    }
}
