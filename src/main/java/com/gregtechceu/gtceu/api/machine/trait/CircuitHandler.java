package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.SingleCustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;
import com.gregtechceu.gtceu.utils.collection.O2LOpenCustomCacheHashMap;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public class CircuitHandler extends NotifiableItemStackHandler {

    public static NotifiableItemStackHandler create(MetaMachine machine) {
        return new CircuitHandler(machine);
    }

    protected CircuitHandler(MetaMachine machine, @NotNull IO capabilityIO, IntFunction<CustomItemStackHandler> storageFactory) {
        super(machine, 1, IO.IN, capabilityIO, storageFactory);
    }

    protected CircuitHandler(MetaMachine machine) {
        this(machine, IO.NONE, ItemStackHandler::new);
        setFilter(IntCircuitBehaviour::isIntegratedCircuit);
    }

    @Override
    public boolean isNotConsumable() {
        return true;
    }

    @Override
    public boolean isRecipeOnly() {
        return true;
    }

    @Override
    public boolean shouldSearchContent() {
        return false;
    }

    @Override
    public boolean isEmpty() {
        if (isEmpty == null) {
            isEmpty = storage.stacks[0].isEmpty();
        }
        return isEmpty;
    }

    @Override
    public @Nullable Object2LongOpenCustomHashMap<ItemStack> getItemMap() {
        if (itemMap == null) {
            itemMap = new O2LOpenCustomCacheHashMap<>(ItemStackHashStrategy.ITEM);
        }
        if (changed) {
            changed = false;
            itemMap.clear();
            ItemStack stored = storage.stacks[0];
            int count = stored.getCount();
            boolean empty = count < 1;
            isEmpty = empty;
            if (!empty) {
                itemMap.put(stored, count);
            }
        }
        return isEmpty ? null : itemMap;
    }

    @Override
    public boolean forEachInputItems(Predicate<ItemStack> function) {
        return function.test(storage.stacks[0]);
    }

    @Override
    public double getTotalContentAmount() {
        return storage.stacks[0].getCount();
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public List<Ingredient> handleRecipe(IO io, GTRecipe recipe, List<?> left, boolean simulate) {
        return handleRecipeInner(io, recipe, (List<Ingredient>) left, simulate);
    }

    @Override
    public List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> left, boolean simulate) {
        if (simulate && io == IO.IN) {
            ItemStack stored = storage.stacks[0];
            int count = stored.getCount();
            if (count == 1) {
                left = new ObjectArrayList<>(left);
                for (var it = left.listIterator(0); it.hasNext();) {
                    if (it.next().test(stored)) {
                        it.remove();
                        break;
                    }
                }
            }
        }
        return left.isEmpty() ? null : left;
    }

    public static class ItemStackHandler extends SingleCustomItemStackHandler {

        protected ItemStackHandler(int size) {
            super(size);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public int insertItemFast(int slot, @NotNull ItemStack stack, int count, boolean simulate) {
            return 0;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }
    }
}
