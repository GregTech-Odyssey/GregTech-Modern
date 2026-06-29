package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.api.transfer.fluid.ICustomFluidStackHandler;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.SimpleStack;
import com.gregtechceu.gtceu.utils.function.ObjLongPredicate;

import net.minecraft.core.Direction;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;

import com.fast.recipesearch.IntLongMap;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.ObjLongConsumer;
import java.util.function.Predicate;

public class NotifiableFluidTank extends NotifiableContentHandler implements ICapabilityTrait, ICustomFluidStackHandler {

    @Getter
    public final IO capabilityIO;
    @Setter
    @Getter
    protected Predicate<@Nullable Direction> capabilityValidator = GTUtil.FAVORABLE;
    @Getter
    @SaveToDisk
    protected final CustomFluidTank[] storages;
    protected boolean allowSameFluids = true;

    @Getter
    @SaveToDisk
    @SyncToClient
    protected final CustomFluidTank lockedFluid = new CustomFluidTank(FluidType.BUCKET_VOLUME);
    @SaveToDisk(defaultValue = "false")
    public boolean isVoiding;
    @Getter
    protected Predicate<FluidStack> filter = GTUtil.FAVORABLE;

    public NotifiableFluidTank(MetaMachine machine, int slots, int capacity, IO handlerIO, IO capabilityIO) {
        super(machine, handlerIO);
        this.storages = new CustomFluidTank[slots];
        this.capabilityIO = capabilityIO;
        for (int i = 0; i < this.storages.length; i++) {
            this.storages[i] = new CustomFluidTank(capacity);
            this.storages[i].setOnContentsChanged(this::onContentsChanged);
        }
        if (slots > 1 && handlerIO == IO.IN) allowSameFluids = false;
    }

    public NotifiableFluidTank(MetaMachine machine, List<CustomFluidTank> storages, IO io, IO capabilityIO) {
        super(machine, io);
        this.storages = storages.toArray(CustomFluidTank[]::new);
        this.capabilityIO = capabilityIO;
        for (CustomFluidTank storage : this.storages) {
            storage.setOnContentsChanged(this::onContentsChanged);
        }
    }

    public NotifiableFluidTank(MetaMachine machine, int slots, int capacity, IO io) {
        this(machine, slots, capacity, io, io);
    }

    public NotifiableFluidTank(MetaMachine machine, List<CustomFluidTank> storages, IO io) {
        this(machine, storages, io, io);
    }

    @Override
    public boolean canHandleFluid() {
        return true;
    }

    @Override
    public boolean handleRecipeFluid(IO io, GTRecipe recipe, List<Content<FluidIngredient>> fluids, boolean simulate) {
        if (io != handlerIO) throw new IllegalStateException("IO is not the same");
        if (simulate) {
            return handleRecipeSimulate(io, fluids, storages, lockedFluid);
        } else {
            return handleRecipe(io, fluids, storages, lockedFluid);
        }
    }

    public static boolean handleRecipe(IO io, List<Content<FluidIngredient>> fluids, CustomFluidTank[] storages, CustomFluidTank lockedFluid) {
        var length = storages.length;
        Runnable[] listeners = new Runnable[length];
        for (int i = 0; i < length; i++) {
            listeners[i] = storages[i].getOnContentsChanged();
            storages[i].setOnContentsChanged(GTUtil.NOOP);
        }
        boolean changed = false;
        for (var it = fluids.iterator(); it.hasNext();) {
            var ingredient = it.next();
            if (ingredient.isEmpty()) {
                it.remove();
                continue;
            }
            if (io == IO.IN) {
                for (CustomFluidTank storage : storages) {
                    var stored = storage.getFluid();
                    int amount = stored.getAmount();
                    if (amount == 0) continue;
                    if (ingredient.inner.test(stored)) {
                        var drained = storage.drain(ingredient.getIntAmount(), FluidAction.EXECUTE);
                        if (drained.getAmount() > 0) {
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
                var fluid = ingredient.inner.getFluid();
                if (fluid == null) {
                    it.remove();
                    continue;
                }
                for (CustomFluidTank storage : storages) {
                    var stored = storage.getFluid();
                    int amount = stored.getAmount();
                    if (amount < storage.getCapacity() && (lockedFluid.isEmpty() || lockedFluid.getFluid().getFluid() == fluid) && (stored.isEmpty() || stored.getFluid() == fluid)) {
                        FluidStack output = ingredient.inner.getFluidStack(ingredient.getIntAmount());
                        int filled = storage.fill(output, FluidAction.EXECUTE);
                        if (filled > 0) {
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
        for (int i = 0; i < length; i++) {
            storages[i].setOnContentsChanged(listeners[i]);
            if (changed) listeners[i].run();
        }
        return fluids.isEmpty();
    }

    public static boolean handleRecipeSimulate(IO io, List<Content<FluidIngredient>> fluids, CustomFluidTank[] storages, CustomFluidTank lockedFluid) {
        var length = storages.length;
        SimpleStack<FluidStack>[] visiteds = new SimpleStack[length];
        for (var it = fluids.iterator(); it.hasNext();) {
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
                    if (ingredient.inner.test(stored)) {
                        var drained = storage.drain(ingredient.getIntAmount(), IFluidHandler.FluidAction.SIMULATE);
                        if (drained.getAmount() > 0) {
                            visiteds[tank] = new SimpleStack<>(drained, amount - drained.getAmount());
                            ingredient.shrink(drained.getAmount());
                            if (ingredient.amount <= 0) {
                                it.remove();
                                break;
                            }
                        }
                    }
                }
            } else {
                var fluid = ingredient.inner.getFluid();
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
                        FluidStack output = ingredient.inner.getFluidStack(ingredient.getIntAmount());
                        int filled = storage.fill(output, IFluidHandler.FluidAction.SIMULATE);
                        if (filled > 0) {
                            visiteds[tank] = new SimpleStack<>(output, filled);
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
        return fluids.isEmpty();
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

    public int getTanks() {
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
    public void fillSearchMap(@NotNull GTRecipeType type, @NotNull IntLongMap map) {
        var tanks = getTanks();
        for (int i = 0; i < tanks; ++i) {
            var stack = getFluidInTank(i);
            var amount = stack.getAmount();
            if (amount > 0) {
                type.convertFluid(stack, amount, map);
            }
        }
    }

    @Override
    public boolean updateEmpty() {
        for (CustomFluidTank storage : storages) {
            if (!storage.getFluid().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void exportToNearby(@NotNull Direction... facings) {
        if (isEmpty()) return;
        var level = getMachine().getLevel();
        var pos = getMachine().getPos();
        for (Direction facing : facings) {
            var filter = getMachine().getFluidCapFilter(facing, IO.OUT);
            machine.holder.blockEntityDirectionCache.getAdjacentFluidHandler(level, pos, facing).ifPresent(adj -> GTTransferUtils.transferFluidsFiltered(this, adj, filter));
        }
    }

    public void importFromNearby(@NotNull Direction... facings) {
        var level = getMachine().getLevel();
        var pos = getMachine().getPos();
        for (Direction facing : facings) {
            var filter = getMachine().getFluidCapFilter(facing, IO.IN);
            machine.holder.blockEntityDirectionCache.getAdjacentFluidHandler(level, pos, facing).ifPresent(adj -> GTTransferUtils.transferFluidsFiltered(adj, this, filter));
        }
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

    @Override
    public int fillInternal(FluidStack resource, FluidAction action) {
        var amount = resource.getAmount();
        if (amount < 1) return 0;
        var filled = 0;
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
                var f = storage.fill(ICustomFluidStackHandler.copy(resource, amount - filled), action);
                if (f > 0) {
                    filled += f;
                    if (!allowSameFluids) {
                        break;
                    }
                }
                if (filled >= amount) break;
            }
        } else {
            filled = existingStorage.fill(resource.copy(), action);
        }
        return isVoiding && canVoidResource ? amount : filled;
    }

    @NotNull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (canCapOutput()) {
            return drainInternal(resource, action);
        }
        return FluidStack.EMPTY;
    }

    @Override
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

    public NotifiableFluidTank setAvailable(boolean available) {
        this.isAvailable = available;
        return this;
    }
}
