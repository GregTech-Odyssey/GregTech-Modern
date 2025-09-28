package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.lookup.IntIngredientMap;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.SingleCustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.utils.function.ObjectLongConsumer;
import com.gregtechceu.gtceu.utils.function.ObjectLongPredicate;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.IntFunction;

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
    public boolean forEachItems(ObjectLongPredicate<ItemStack> function) {
        var stack = storage.stacks[0];
        var amount = stack.getCount();
        if (amount > 0) {
            return function.test(stack, amount);
        }
        return false;
    }

    @Override
    public void fastForEachItems(ObjectLongConsumer<ItemStack> function) {
        var stack = storage.stacks[0];
        var amount = stack.getCount();
        if (amount > 0) {
            function.accept(stack, amount);
        }
    }

    @Override
    public IntIngredientMap getIngredientMap() {
        if (changed) {
            changed = false;
            intIngredientMap.clear();
            var stack = storage.stacks[0];
            var amount = stack.getCount();
            if (amount > 0) {
                IntIngredientMap.ITEM_CONVERSION.convert(stack, amount, intIngredientMap);
            }
        }
        return intIngredientMap;
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
