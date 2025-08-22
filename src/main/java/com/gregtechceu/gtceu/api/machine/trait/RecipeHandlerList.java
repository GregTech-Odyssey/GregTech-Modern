package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.recipe.lookup.IntIngredientMap;

import com.lowdragmc.lowdraglib.syncdata.ISubscription;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class RecipeHandlerList {

    public static final RecipeHandlerList NO_DATA = new RecipeHandlerList(IO.NONE);

    public static Consumer<MultiblockPartMachine> NOTIFY = p -> {};

    public final Reference2ObjectOpenHashMap<RecipeCapability<?>, List<IRecipeHandler<?>>> handlerMap = new Reference2ObjectOpenHashMap<>();
    public final List<IRecipeHandler<?>> allHandlers = new ObjectArrayList<>();
    public final List<NotifiableRecipeHandlerTrait<?>> allHandlerTraits = new ObjectArrayList<>();
    private final IO handlerIO;
    private int color = -1;
    private boolean isDistinct;

    public final IntIngredientMap intIngredientMap = new IntIngredientMap();
    public boolean change = true;

    public final MultiblockPartMachine part;

    protected RecipeHandlerList(IO handlerIO, MultiblockPartMachine part) {
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

    public static RecipeHandlerList of(IO io, MultiblockPartMachine part, Iterable<IRecipeHandler<?>> handlers) {
        RecipeHandlerList rhl = new RecipeHandlerList(io, part);
        rhl.addHandlers(handlers);
        return rhl;
    }

    public void addHandler(IRecipeHandler<?> handler) {
        addHandlers(List.of(handler));
    }

    public void addHandlers(IRecipeHandler<?>... handlers) {
        addHandlers(Arrays.asList(handlers));
    }

    public void addHandlers(Iterable<IRecipeHandler<?>> handlers) {
        for (var handler : handlers) {
            handlerMap.computeIfAbsent(handler.getCapability(), c -> new ObjectArrayList<>()).add(handler);
            allHandlers.add(handler);
            if (handler instanceof NotifiableRecipeHandlerTrait<?> rht) allHandlerTraits.add(rht);
        }
        if (handlerIO == IO.OUT) sort();
    }

    private void sort() {
        for (var list : handlerMap.values()) {
            list.sort(IRecipeHandler.ENTRY_COMPARATOR);
        }
    }

    public final void setDistinctAndNotify(boolean distinct) {
        setDistinct(distinct);
        if (part != null) {
            NOTIFY.accept(part);
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

    public boolean isDistinct() {
        return isDistinct;
    }

    public void setColor(int color) {
        setColor(color, false);
    }

    public void setColor(int color, boolean notify) {
        this.color = color;
        if (notify && part != null) {
            NOTIFY.accept(part);
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

    private record Subscription(List<ISubscription> subs) implements ISubscription {

        @Override
        public void unsubscribe() {
            subs.forEach(ISubscription::unsubscribe);
        }
    }

    public ISubscription subscribe(Runnable listener) {
        List<ISubscription> subs = new ObjectArrayList<>(allHandlerTraits.size());
        allHandlerTraits.forEach(rht -> subs.add(rht.addChangedListener(listener)));
        return new Subscription(subs);
    }

    public ISubscription subscribe(Runnable listener, RecipeCapability<?> cap) {
        var capList = getCapability(cap);
        List<ISubscription> subs = new ObjectArrayList<>(capList.size());
        for (var handler : capList) {
            if (handler instanceof IRecipeHandlerTrait<?> trait) {
                subs.add(trait.addChangedListener(listener));
            }
        }
        return new Subscription(subs);
    }

    public IO getHandlerIO() {
        return this.handlerIO;
    }

    public int getColor() {
        return this.color;
    }
}
