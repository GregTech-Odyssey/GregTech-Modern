package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.SimpleStack;
import com.gregtechceu.gtceu.utils.function.ObjLongPredicate;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;

import net.minecraft.core.Direction;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;

import com.fast.recipesearch.IntLongMap;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.ObjLongConsumer;
import java.util.function.Predicate;

public class NotifiableFluidTank extends NotifiableRecipeHandlerTrait<FluidIngredient> implements ICapabilityTrait, IFluidHandlerModifiable {

    @Getter
    public final IO handlerIO;
    @Getter
    public final IO capabilityIO;
    @Getter
    @Persisted
    protected final CustomFluidTank[] storages;
    protected boolean allowSameFluids = true; // Can different tanks be filled with the same fluid. It should be
                                              // determined
    // while creating tanks.
    protected Boolean isEmpty;
    protected boolean changed = true;
    @Getter
    @Persisted
    @DescSynced
    protected final CustomFluidTank lockedFluid = new CustomFluidTank(FluidType.BUCKET_VOLUME);
    @Persisted
    public boolean isVoiding;
    @Getter
    protected Predicate<FluidStack> filter = GTUtil.FAVORABLE;

    protected boolean isAvailable = true;

    protected final IntLongMap intIngredientMap = new IntLongMap();

    public NotifiableFluidTank(MetaMachine machine, int slots, int capacity, IO io, IO capabilityIO) {
        super(machine);
        this.handlerIO = io;
        this.storages = new CustomFluidTank[slots];
        this.capabilityIO = capabilityIO;
        for (int i = 0; i < this.storages.length; i++) {
            this.storages[i] = new CustomFluidTank(capacity);
            this.storages[i].setOnContentsChangedAndfreeze(this::onContentsChanged);
        }
        if (slots > 1 && io == IO.IN) allowSameFluids = false;
    }

    public NotifiableFluidTank(MetaMachine machine, List<CustomFluidTank> storages, IO io, IO capabilityIO) {
        super(machine);
        this.handlerIO = io;
        this.storages = storages.toArray(CustomFluidTank[]::new);
        this.capabilityIO = capabilityIO;
        for (CustomFluidTank storage : this.storages) {
            storage.setOnContentsChangedAndfreeze(this::onContentsChanged);
        }
    }

    public NotifiableFluidTank(MetaMachine machine, int slots, int capacity, IO io) {
        this(machine, slots, capacity, io, io);
    }

    public NotifiableFluidTank(MetaMachine machine, List<CustomFluidTank> storages, IO io) {
        this(machine, storages, io, io);
    }

    public void onContentsChanged() {
        isEmpty = null;
        changed = true;
        machine.onChanged();
        notifyListeners();
    }

    @Override
    public List<FluidIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<FluidIngredient> left, boolean simulate) {
        if (io != handlerIO) return left;
        if (io != IO.IN && io != IO.OUT) return left.isEmpty() ? null : left;
        Runnable[] listeners = null;
        var length = storages.length;
        if (!simulate) {
            listeners = new Runnable[length];
            for (int i = 0; i < length; i++) {
                listeners[i] = storages[i].getOnContentsChanged();
                storages[i].setOnContentsChangedAndfreeze(GTUtil.NOOP);
            }
        }
        boolean changed = false;
        SimpleStack<FluidStack>[] visiteds = new SimpleStack[length];
        IFluidHandler.FluidAction action = simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE;
        for (var it = left.iterator(); it.hasNext();) {
            var ingredient = it.next();
            if (ingredient.isEmpty()) {
                it.remove();
                continue;
            }
            if (io == IO.IN) {
                for (int tank = 0; tank < length; ++tank) {
                    var storage = storages[tank];
                    var visited = visiteds[tank];
                    var stored = storage.getFluid();
                    int amount = (visited == null ? stored.getAmount() : visited.getAmount());
                    if (amount == 0) continue;
                    if (ingredient.test(stored)) {
                        var drained = storage.drain(ingredient.getAmount(), action);
                        if (drained.getAmount() > 0) {
                            if (simulate) {
                                visiteds[tank] = new SimpleStack<>(drained, amount - drained.getAmount());
                            }
                            changed = true;
                            ingredient.shrink(drained.getAmount());
                            if (ingredient.amount <= 0) {
                                it.remove();
                                break;
                            }
                        }
                    }
                }
            } else {
                var fluid = ingredient.getFluid();
                if (fluid == null) {
                    it.remove();
                    continue;
                }
                for (int tank = 0; tank < length; ++tank) {
                    var storage = storages[tank];
                    var visited = visiteds[tank];
                    var stored = storage.getFluid();
                    int amount = (visited == null ? stored.getAmount() : visited.getAmount());
                    if (amount < storage.getCapacity() && (lockedFluid.isEmpty() || lockedFluid.getFluid().getFluid() == fluid) && (stored.isEmpty() || stored.getFluid() == fluid) && (visited == null || visited.inner.getFluid() == fluid)) {
                        FluidStack output = new FluidStack(fluid, ingredient.getAmount(), ingredient.nbt);
                        int filled = storage.fill(output, action);
                        if (filled > 0) {
                            if (simulate) {
                                visiteds[tank] = new SimpleStack<>(output, filled);
                            }
                            changed = true;
                            ingredient.shrink(filled);
                            if (ingredient.amount <= 0) {
                                it.remove();
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (listeners != null) {
            for (int i = 0; i < length; i++) {
                storages[i].setOnContentsChangedAndfreeze(listeners[i]);
                if (changed) listeners[i].run();
            }
        }
        return left.isEmpty() ? null : left;
    }

    @Override
    public int getPriority() {
        return !isLocked() || lockedFluid.getFluid().isEmpty() ? super.getPriority() : HIGH - getTanks();
    }

    public boolean isLocked() {
        return !lockedFluid.getFluid().isEmpty();
    }

    public void setLocked(boolean locked) {
        setLocked(locked, storages[0].getFluid());
    }

    public void setLocked(boolean locked, FluidStack fluidStack) {
        if (this.isLocked() == locked) return;
        if (locked && !fluidStack.isEmpty()) {
            this.lockedFluid.setFluid(fluidStack.copy());
            this.lockedFluid.getFluid().setAmount(1);
            setFilter(stack -> stack.isFluidEqual(this.lockedFluid.getFluid()));
        } else {
            this.lockedFluid.setFluid(FluidStack.EMPTY);
            setFilter(GTUtil.FAVORABLE);
        }
        notifyListeners();
    }

    public NotifiableFluidTank setFilter(Predicate<FluidStack> filter) {
        this.filter = filter;
        for (CustomFluidTank storage : storages) {
            storage.setValidator(filter);
        }
        return this;
    }

    @Override
    public RecipeCapability<FluidIngredient> getCapability() {
        return FluidRecipeCapability.CAP;
    }

    public int getTanks() {
        return storages.length;
    }

    @Override
    public int getSize() {
        return storages.length;
    }

    @Override
    public boolean forEachFluids(ObjLongPredicate<FluidStack> function) {
        var tanks = getTanks();
        for (int i = 0; i < tanks; ++i) {
            var stack = getFluidInTank(i);
            var amount = stack.getAmount();
            if (amount > 0) {
                if (function.test(stack, amount)) return true;
            }
        }
        return false;
    }

    @Override
    public void fastForEachFluids(ObjLongConsumer<FluidStack> function) {
        var tanks = getTanks();
        for (int i = 0; i < tanks; ++i) {
            var stack = getFluidInTank(i);
            var amount = stack.getAmount();
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
            var tanks = getTanks();
            for (int i = 0; i < tanks; ++i) {
                var stack = getFluidInTank(i);
                var amount = stack.getAmount();
                if (amount > 0) {
                    type.convertFluid(stack, amount, intIngredientMap);
                }
            }
        }
        return intIngredientMap;
    }

    @Override
    public boolean isEmpty() {
        if (isEmpty == null) {
            isEmpty = true;
            for (CustomFluidTank storage : storages) {
                if (!storage.getFluid().isEmpty()) {
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
            var filter = getMachine().getFluidCapFilter(facing, IO.OUT);
            machine.blockEntityDirectionCache.getAdjacentFluidHandler(level, pos, facing).ifPresent(adj -> GTTransferUtils.transferFluidsFiltered(this, adj, filter));
        }
    }

    public void importFromNearby(@NotNull Direction... facings) {
        var level = getMachine().getLevel();
        var pos = getMachine().getPos();
        for (Direction facing : facings) {
            var filter = getMachine().getFluidCapFilter(facing, IO.IN);
            machine.blockEntityDirectionCache.getAdjacentFluidHandler(level, pos, facing).ifPresent(adj -> GTTransferUtils.transferFluidsFiltered(adj, this, filter));
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
    public FluidStack getFluidInTank(int tank) {
        return storages[tank].getFluid();
    }

    public void setFluidInTank(int tank, @NotNull FluidStack fluidStack) {
        storages[tank].setFluid(fluidStack);
    }

    @Override
    public int getTankCapacity(int tank) {
        return storages[tank].getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return storages[tank].isFluidValid(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (!canCapInput()) return 0;
        return fillInternal(resource, action);
    }

    public int fillInternal(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return 0;
        var copied = resource.copy();
        boolean canVoidResource = false;
        CustomFluidTank existingStorage = null;
        if (!allowSameFluids) {
            for (var storage : storages) {
                if (!storage.getFluid().isEmpty() && storage.getFluid().isFluidEqual(resource)) {
                    existingStorage = storage;
                    canVoidResource = true;
                    break;
                }
            }
        }
        if (existingStorage == null) {
            for (var storage : storages) {
                boolean canFillStorage = storage.isFluidValid(resource) && (storage.getFluid().isEmpty() || storage.getFluid().isFluidEqual(resource));
                if (canFillStorage) {
                    canVoidResource = true;
                }
                var filled = storage.fill(copied.copy(), action);
                if (filled > 0) {
                    copied.shrink(filled);
                    if (!allowSameFluids) {
                        break;
                    }
                }
                if (copied.isEmpty()) break;
            }
        } else {
            copied.shrink(existingStorage.fill(copied.copy(), action));
        }
        return resource.getAmount() - (isVoiding && canVoidResource ? 0 : copied.getAmount());
    }

    @NotNull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (canCapOutput()) {
            return drainInternal(resource, action);
        }
        return FluidStack.EMPTY;
    }

    public FluidStack drainInternal(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;
        var copied = resource.copy();
        for (var storage : storages) {
            var candidate = copied.copy();
            copied.shrink(storage.drain(candidate, action).getAmount());
            if (copied.isEmpty()) break;
        }
        copied.setAmount(resource.getAmount() - copied.getAmount());
        return copied;
    }

    @NotNull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (canCapOutput()) {
            return drainInternal(maxDrain, action);
        }
        return FluidStack.EMPTY;
    }

    public FluidStack drainInternal(int maxDrain, FluidAction action) {
        if (maxDrain == 0) return FluidStack.EMPTY;
        FluidStack totalDrained = null;
        for (var storage : storages) {
            if (totalDrained == null || totalDrained.isEmpty()) {
                totalDrained = storage.drain(maxDrain, action);
                if (totalDrained.isEmpty()) {
                    totalDrained = null;
                } else {
                    maxDrain -= totalDrained.getAmount();
                }
            } else {
                FluidStack copy = totalDrained.copy();
                copy.setAmount(maxDrain);
                FluidStack drain = storage.drain(copy, action);
                totalDrained.grow(drain.getAmount());
                maxDrain -= drain.getAmount();
            }
            if (maxDrain <= 0) break;
        }
        return totalDrained == null ? FluidStack.EMPTY : totalDrained;
    }

    @Override
    public void onMachineLoad() {
        super.onMachineLoad();
        if (this.isLocked()) {
            setFilter(stack -> stack.isFluidEqual(this.lockedFluid.getFluid()));
        }
    }

    @Override
    public boolean isAvailable() {
        return this.isAvailable;
    }

    public NotifiableFluidTank setAvailable(boolean available) {
        this.isAvailable = available;
        return this;
    }
}
