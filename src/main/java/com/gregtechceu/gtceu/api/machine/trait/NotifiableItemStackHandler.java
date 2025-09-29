package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.recipe.lookup.IntIngredientMap;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.function.ObjectLongConsumer;
import com.gregtechceu.gtceu.utils.function.ObjectLongPredicate;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public class NotifiableItemStackHandler extends NotifiableRecipeHandlerTrait<Ingredient> implements ICapabilityTrait, IItemHandlerModifiable {

    public static NotifiableItemStackHandler empty(MetaMachine machine) {
        return new NotifiableItemStackHandler(machine, 0, IO.NONE).shouldSearchContent(false);
    }

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(NotifiableItemStackHandler.class, NotifiableRecipeHandlerTrait.MANAGED_FIELD_HOLDER);
    public final IO handlerIO;
    public final IO capabilityIO;
    @Persisted
    @DescSynced
    public final CustomItemStackHandler storage;
    private boolean shouldSearchContent = true;
    protected Boolean isEmpty;
    protected boolean changed = true;

    protected boolean isAvailable = true;

    protected final IntIngredientMap intIngredientMap = new IntIngredientMap();

    public NotifiableItemStackHandler(MetaMachine machine, int slots, @NotNull IO handlerIO, @NotNull IO capabilityIO, IntFunction<CustomItemStackHandler> storageFactory) {
        super(machine);
        this.handlerIO = handlerIO;
        this.storage = storageFactory.apply(slots);
        this.capabilityIO = capabilityIO;
        this.storage.setOnContentsChangedAndfreeze(this::onContentsChanged);
    }

    public NotifiableItemStackHandler(MetaMachine machine, int slots, @NotNull IO handlerIO, @NotNull IO capabilityIO) {
        this(machine, slots, handlerIO, capabilityIO, CustomItemStackHandler::new);
    }

    public NotifiableItemStackHandler(MetaMachine machine, int slots, @NotNull IO handlerIO) {
        this(machine, slots, handlerIO, handlerIO);
    }

    public NotifiableItemStackHandler setFilter(Predicate<ItemStack> filter) {
        this.storage.setFilter(filter);
        return this;
    }

    public void onContentsChanged() {
        isEmpty = null;
        changed = true;
        machine.onChanged();
        notifyListeners();
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public List<Ingredient> handleRecipeInner(IO io, GTRecipe recipe, List<Ingredient> left, boolean simulate) {
        return handleRecipe(io, recipe, left, simulate, handlerIO, storage);
    }

    // TODO: See if implementable in outside callers and unstatic; or move to different common class if not
    // Notable caller is ItemRecipeHandler, used for MinerLogic
    public static List<Ingredient> handleRecipe(IO io, GTRecipe recipe, List<Ingredient> left, boolean simulate, IO handlerIO, CustomItemStackHandler storage) {
        if (io != handlerIO) return left;
        if (io != IO.IN && io != IO.OUT) return left.isEmpty() ? null : left;
        // Store the ItemStack in each slot after an operation
        // Necessary for simulation since we don't actually modify the slot's contents
        // Doesn't hurt for execution, and definitely cheaper than copying the entire storage
        ItemStack[] visited = new ItemStack[storage.size];
        for (var it = left.listIterator(0); it.hasNext();) {
            var ingredient = it.next();
            if (ingredient.isEmpty()) {
                it.remove();
                continue;
            }
            ItemStack[] items;
            int amount;
            items = ingredient.getItems();
            if (items.length == 0 || items[0].isEmpty()) {
                it.remove();
                continue;
            }
            if (ingredient instanceof SizedIngredient si) amount = si.getAmount();
            else amount = items[0].getCount();
            for (int slot = 0; slot < storage.size; ++slot) {
                ItemStack current = visited[slot] == null ? storage.stacks[slot] : visited[slot];
                int count = current.getCount();
                if (io == IO.IN) {
                    if (current.isEmpty()) continue;
                    if (ingredient.test(current)) {
                        var extracted = storage.extractItem(slot, Math.min(count, amount), simulate);
                        if (!extracted.isEmpty()) {
                            visited[slot] = extracted.copyWithCount(count - extracted.getCount());
                        }
                        amount -= extracted.getCount();
                    }
                } else {
                    // IO.OUT
                    ItemStack output = items[0].copyWithCount(amount);
                    // Only try this slot if not visited or if visited with the same type of item
                    if (visited[slot] == null || ItemStack.isSameItemSameTags(visited[slot], output)) {
                        if (count < output.getMaxStackSize() && count < storage.getSlotLimit(slot)) {
                            var remainder = storage.insertItem(slot, output, simulate);
                            if (remainder.getCount() < amount) {
                                visited[slot] = output.copyWithCount(count + amount - remainder.getCount());
                            }
                            amount = remainder.getCount();
                        }
                    }
                }
                if (amount <= 0) {
                    it.remove();
                    break;
                }
            }
            // Modify ingredient if we didn't finish it off
            if (amount > 0) {
                if (ingredient instanceof SizedIngredient si) {
                    si.setAmount(amount);
                } else {
                    items[0].setCount(amount);
                }
            }
        }
        return left.isEmpty() ? null : left;
    }

    @Override
    public RecipeCapability<Ingredient> getCapability() {
        return ItemRecipeCapability.CAP;
    }

    public int getSlots() {
        return storage.size;
    }

    @Override
    public int getSize() {
        return storage.size;
    }

    @Override
    public boolean forEachItems(ObjectLongPredicate<ItemStack> function) {
        for (int i = 0; i < storage.size; ++i) {
            var stack = storage.stacks[i];
            var amount = stack.getCount();
            if (amount > 0) {
                if (function.test(stack, amount)) return true;
            }
        }
        return false;
    }

    @Override
    public void fastForEachItems(ObjectLongConsumer<ItemStack> function) {
        for (int i = 0; i < storage.size; ++i) {
            var stack = storage.stacks[i];
            var amount = stack.getCount();
            if (amount > 0) {
                function.accept(stack, amount);
            }
        }
    }

    @Override
    public IntIngredientMap getIngredientMap() {
        if (changed) {
            changed = false;
            intIngredientMap.clear();
            for (int i = 0; i < storage.size; ++i) {
                var stack = storage.stacks[i];
                var amount = stack.getCount();
                if (amount > 0) {
                    IntIngredientMap.ITEM_CONVERSION.convert(stack, amount, intIngredientMap);
                }
            }
        }
        return intIngredientMap;
    }

    @Override
    public boolean isEmpty() {
        if (isEmpty == null) {
            isEmpty = true;
            for (int i = 0; i < storage.size; i++) {
                if (!storage.stacks[i].isEmpty()) {
                    isEmpty = false;
                    break;
                }
            }
        }
        return isEmpty;
    }

    public void exportToNearby(@NotNull Direction... facings) {
        if (isEmpty()) return;
        var level = getMachine().getLevel();
        var pos = getMachine().getPos();
        for (Direction facing : facings) {
            var filter = getMachine().getItemCapFilter(facing, IO.OUT);
            machine.blockEntityDirectionCache.getAdjacentItemHandler(level, pos, facing).ifPresent(adj -> GTTransferUtils.transferItemsFiltered(this, adj, filter));
        }
    }

    public void importFromNearby(@NotNull Direction... facings) {
        var level = getMachine().getLevel();
        var pos = getMachine().getPos();
        for (Direction facing : facings) {
            var filter = getMachine().getItemCapFilter(facing, IO.IN);
            machine.blockEntityDirectionCache.getAdjacentItemHandler(level, pos, facing).ifPresent(adj -> GTTransferUtils.transferItemsFiltered(adj, this, filter));
        }
    }

    //////////////////////////////////////
    // ******* Capability ********//
    //////////////////////////////////////
    @Override
    public boolean hasCapability(@Nullable Direction side) {
        if (capabilityIO == IO.NONE) return false;
        return isAvailable && capabilityValidator.test(side);
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return storage.stacks[slot];
    }

    @Override
    public void setStackInSlot(int index, @NotNull ItemStack stack) {
        storage.setStackInSlot(index, stack);
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (canCapInput()) {
            return storage.insertItem(slot, stack, simulate);
        }
        return stack;
    }

    public ItemStack insertItemInternal(int slot, @NotNull ItemStack stack, boolean simulate) {
        return storage.insertItem(slot, stack, simulate);
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (canCapOutput()) {
            return storage.extractItem(slot, amount, simulate);
        }
        return ItemStack.EMPTY;
    }

    public ItemStack extractItemInternal(int slot, int amount, boolean simulate) {
        return storage.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return storage.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return storage.isItemValid(slot, stack);
    }

    public IO getHandlerIO() {
        return this.handlerIO;
    }

    public IO getCapabilityIO() {
        return this.capabilityIO;
    }

    public boolean shouldSearchContent() {
        return this.shouldSearchContent;
    }

    /**
     * @return {@code this}.
     */
    public NotifiableItemStackHandler shouldSearchContent(final boolean shouldSearchContent) {
        this.shouldSearchContent = shouldSearchContent;
        return this;
    }

    @Override
    public boolean isAvailable() {
        return this.isAvailable;
    }

    public NotifiableItemStackHandler setAvailable(boolean available) {
        this.isAvailable = available;
        return this;
    }
}
