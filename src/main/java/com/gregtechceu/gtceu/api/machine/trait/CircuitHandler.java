package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.SingleCustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.utils.function.ObjLongPredicate;

import net.minecraft.world.item.ItemStack;

import com.fast.recipesearch.IntLongMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ObjLongConsumer;

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
    public boolean updateEmpty() {
        return storage.stacks[0].isEmpty();
    }

    @Override
    public boolean forEachItems(ObjLongPredicate<ItemStack> function) {
        var stack = storage.stacks[0];
        var amount = stack.getCount();
        if (amount > 0) {
            return function.test(stack, amount);
        }
        return false;
    }

    @Override
    public void fastForEachItems(ObjLongConsumer<ItemStack> function) {
        var stack = storage.stacks[0];
        var amount = stack.getCount();
        if (amount > 0) {
            function.accept(stack, amount);
        }
    }

    @Override
    public void fillSearchMap(@NotNull GTRecipeType type, @NotNull IntLongMap map) {
        var stack = storage.stacks[0];
        var amount = stack.getCount();
        if (amount > 0) {
            type.convertItem(stack, amount, map);
        }
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean handleRecipeItem(IO io, GTRecipe recipe, List<Content<ItemIngredient>> items, boolean simulate) {
        if (simulate && io == IO.IN) {
            ItemStack stored = storage.stacks[0];
            if (stored.getCount() > 0) {
                for (var it = items.iterator(); it.hasNext();) {
                    var content = it.next();
                    if (content.chance == 0 && content.inner.test(stored)) {
                        it.remove();
                        break;
                    }
                }
            }
        }
        return items.isEmpty();
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
        public int insert(int slot, @NotNull ItemStack stack, int count, boolean simulate) {
            return 0;
        }

        @Override
        public int extract(int slot, int amount, boolean simulate) {
            return 0;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }
    }
}
