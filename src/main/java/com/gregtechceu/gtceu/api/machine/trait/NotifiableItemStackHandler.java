package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.ingredient.ItemIngredient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.utils.GTMath;
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

public class NotifiableItemStackHandler extends NotifiableRecipeHandlerTrait<ItemIngredient> implements ICapabilityTrait, IItemHandlerModifiable {

    public static NotifiableItemStackHandler empty(MetaMachine machine) {
        return new NotifiableItemStackHandler(machine, 0, IO.NONE).shouldSearchContent(false);
    }

    @Getter
    public final IO handlerIO;
    @Getter
    public final IO capabilityIO;
    @Persisted
    public final CustomItemStackHandler storage;
    private boolean shouldSearchContent = true;
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
    public List<ItemIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<ItemIngredient> left, boolean simulate) {
        return handleRecipe(io, left, simulate, handlerIO, storage);
    }

    public static List<ItemIngredient> handleRecipe(IO io, List<ItemIngredient> left, boolean simulate, IO handlerIO, CustomItemStackHandler storage) {
        if (io != handlerIO) return left;
        if (io != IO.IN && io != IO.OUT) return left.isEmpty() ? null : left;
        Runnable listener = null;
        if (!simulate) {
            listener = storage.getOnContentsChanged();
            storage.setOnContentsChangedAndfreeze(GTUtil.NOOP);
        }
        boolean changed = false;
        var size = storage.size;
        SimpleStack<ItemStack>[] visiteds = new SimpleStack[size];
        for (var it = left.iterator(); it.hasNext();) {
            var ingredient = it.next();
            if (ingredient.isEmpty()) {
                it.remove();
                continue;
            }
            long amount = ingredient.amount;
            if (io == IO.IN) {
                for (int slot = 0; slot < size; ++slot) {
                    ItemStack stored = storage.stacks[slot];
                    var visited = visiteds[slot];
                    int count = (visited == null ? stored.getCount() : visited.getAmount());
                    if (count == 0) continue;
                    if (ingredient.test(stored)) {
                        var extracted = storage.extractItem(slot, GTMath.saturatedCast(amount), simulate).getCount();
                        if (extracted > 0) {
                            if (simulate) {
                                visiteds[slot] = new SimpleStack<>(stored, count - extracted);
                            }
                            changed = true;
                            amount -= extracted;
                            if (amount <= 0) {
                                it.remove();
                                break;
                            }
                        }
                    }
                }
            } else {
                var itemStack = ingredient.getInnerItemStack();
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
                        var remainder = storage.insertItemFast(slot, itemStack, GTMath.saturatedCast(amount), simulate);
                        if (remainder < amount) {
                            if (simulate) {
                                visiteds[slot] = new SimpleStack<>(itemStack, remainder);
                            }
                            changed = true;
                            if (remainder <= 0) {
                                it.remove();
                                break;
                            }
                            amount = remainder;
                        }
                    }
                }
            }
            if (amount > 0) ingredient.amount = amount;
        }
        if (listener != null) {
            storage.setOnContentsChangedAndfreeze(listener);
            if (changed) listener.run();
        }
        return left.isEmpty() ? null : left;
    }

    @Override
    public RecipeCapability<ItemIngredient> getCapability() {
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
    public IntLongMap getIngredientMap(@NotNull GTRecipeType type) {
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
