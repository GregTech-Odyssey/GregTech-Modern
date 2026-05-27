package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.SimpleStack;
import com.gregtechceu.gtceu.utils.function.ObjLongPredicate;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.fast.recipesearch.IntLongMap;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ObjLongConsumer;
import java.util.function.Predicate;

public class NotifiableItemStackHandler extends NotifiableRecipeHandlerTrait implements ICapabilityTrait, IItemHandlerModifiable {

    public static NotifiableItemStackHandler empty(MetaMachine machine) {
        return new NotifiableItemStackHandler(machine, 0, IO.NONE).setAvailable(false);
    }

    @Getter
    public final IO handlerIO;
    @Getter
    public final IO capabilityIO;
    @Persisted
    public final CustomItemStackHandler storage;
    protected Boolean isEmpty;
    protected boolean changed = true;

    protected boolean isAvailable = true;

    protected final IntLongMap intIngredientMap = new IntLongMap();

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
    public boolean canHandleItem() {
        return true;
    }

    @Override
    public boolean handleRecipeItem(IO io, GTRecipe recipe, List<Content<ItemIngredient>> items, boolean simulate) {
        if (io != handlerIO) throw new IllegalStateException("IO is not the same");
        if (simulate) {
            return handleRecipeSimulate(io, items, storage);
        } else {
            return handleRecipe(io, items, storage);
        }
    }

    public static boolean handleRecipe(IO io, List<Content<ItemIngredient>> items, CustomItemStackHandler storage) {
        Runnable listener = storage.getOnContentsChanged();
        storage.setOnContentsChangedAndfreeze(GTUtil.NOOP);
        boolean changed = false;
        var size = storage.size;
        for (var it = items.iterator(); it.hasNext();) {
            var ingredient = it.next();
            if (ingredient.isEmpty()) {
                it.remove();
                continue;
            }
            if (io == IO.IN) {
                for (int slot = 0; slot < size; ++slot) {
                    ItemStack stored = storage.stacks[slot];
                    int count = stored.getCount();
                    if (count == 0) continue;
                    if (ingredient.inner.test(stored)) {
                        var extracted = storage.extract(slot, ingredient.getIntAmount(), false);
                        if (extracted > 0) {
                            changed = true;
                            ingredient.shrink(extracted);
                            if (ingredient.amount <= 0) {
                                it.remove();
                                break;
                            }
                        }
                    }
                }
            } else {
                var itemStack = ingredient.inner.getInnerItemStack();
                var item = itemStack.getItem();
                if (item == Items.AIR) {
                    it.remove();
                    continue;
                }
                for (int slot = 0; slot < size; ++slot) {
                    ItemStack stored = storage.stacks[slot];
                    int count = stored.getCount();
                    if (count < itemStack.getMaxStackSize() && count < storage.getSlotLimit(slot) && (count == 0 || stored.is(item))) {
                        var inserted = storage.insert(slot, itemStack, ingredient.getIntAmount(), false);
                        if (inserted > 0) {
                            changed = true;
                            ingredient.shrink(inserted);
                            if (ingredient.amount <= 0) {
                                it.remove();
                                break;
                            }
                        }
                    }
                }
            }
        }
        storage.setOnContentsChangedAndfreeze(listener);
        if (changed) listener.run();
        return items.isEmpty();
    }

    public static boolean handleRecipeSimulate(IO io, List<Content<ItemIngredient>> items, CustomItemStackHandler storage) {
        var size = storage.size;
        SimpleStack<ItemStack>[] visiteds = new SimpleStack[size];
        for (var it = items.iterator(); it.hasNext();) {
            var ingredient = it.next();
            if (ingredient.isEmpty()) {
                it.remove();
                continue;
            }
            if (io == IO.IN) {
                for (int slot = 0; slot < size; ++slot) {
                    ItemStack stored = storage.stacks[slot];
                    var visited = visiteds[slot];
                    int count = (visited == null ? stored.getCount() : visited.getAmount());
                    if (count == 0) continue;
                    if (ingredient.inner.test(stored)) {
                        var extracted = storage.extract(slot, ingredient.getIntAmount(), true);
                        if (extracted > 0) {
                            visiteds[slot] = new SimpleStack<>(stored, count - extracted);
                            ingredient.shrink(extracted);
                            if (ingredient.amount <= 0) {
                                it.remove();
                                break;
                            }
                        }
                    }
                }
            } else {
                var itemStack = ingredient.inner.getInnerItemStack();
                var item = itemStack.getItem();
                if (item == Items.AIR) {
                    it.remove();
                    continue;
                }
                for (int slot = 0; slot < size; ++slot) {
                    ItemStack stored = storage.stacks[slot];
                    var visited = visiteds[slot];
                    int count = (visited == null ? stored.getCount() : visited.getAmount());
                    if (count < itemStack.getMaxStackSize() && count < storage.getSlotLimit(slot) && (count == 0 || stored.is(item)) && (visited == null || visited.inner.is(item))) {
                        var inserted = storage.insert(slot, itemStack, ingredient.getIntAmount(), true);
                        if (inserted > 0) {
                            visiteds[slot] = new SimpleStack<>(itemStack, inserted);
                            ingredient.shrink(inserted);
                            if (ingredient.amount <= 0) {
                                it.remove();
                                break;
                            }
                        }
                    }
                }
            }
        }
        return items.isEmpty();
    }

    @Override
    public int getSlots() {
        return storage.size;
    }

    @Override
    public boolean forEachItems(ObjLongPredicate<ItemStack> function) {
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
    public void fastForEachItems(ObjLongConsumer<ItemStack> function) {
        for (int i = 0; i < storage.size; ++i) {
            var stack = storage.stacks[i];
            var amount = stack.getCount();
            if (amount > 0) {
                function.accept(stack, amount);
            }
        }
    }

    @Override
    public IntLongMap getSearchMap(@NotNull GTRecipeType type) {
        if (changed) {
            changed = false;
            intIngredientMap.clear();
            for (int i = 0; i < storage.size; ++i) {
                var stack = storage.stacks[i];
                var amount = stack.getCount();
                if (amount > 0) {
                    type.convertItem(stack, amount, intIngredientMap);
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

    @Override
    public boolean isAvailable() {
        return this.isAvailable;
    }

    public NotifiableItemStackHandler setAvailable(boolean available) {
        this.isAvailable = available;
        return this;
    }
}
