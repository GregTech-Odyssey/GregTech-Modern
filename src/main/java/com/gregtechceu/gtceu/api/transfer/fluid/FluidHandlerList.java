package com.gregtechceu.gtceu.api.transfer.fluid;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

public class FluidHandlerList implements IFluidHandlerModifiable, INBTSerializable<CompoundTag> {

    public final IFluidHandler[] handlers;
    protected final int size;
    @Setter
    protected Predicate<FluidStack> filter = GTUtil.FAVORABLE;

    public FluidHandlerList(IFluidHandler... handlers) {
        this.handlers = handlers;
        int size = 0;
        for (IFluidHandler handler : handlers) {
            size += handler.getTanks();
        }
        this.size = size;
    }

    public FluidHandlerList(List<IFluidHandler> handlers) {
        this(handlers.toArray(new IFluidHandler[0]));
    }

    @Override
    public int getTanks() {
        return size;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        for (IFluidHandler handler : handlers) {
            var tanks = handler.getTanks();
            if (tank < tanks) {
                return handler.getFluidInTank(tank);
            }
            tank -= tanks;
        }
        return FluidStack.EMPTY;
    }

    @Override
    public void setFluidInTank(int tank, FluidStack stack) {
        for (IFluidHandler handler : handlers) {
            var tanks = handler.getTanks();
            if (tank < tanks) {
                if (handler instanceof IFluidHandlerModifiable modifiable) modifiable.setFluidInTank(tank, stack);
                return;
            }
            tank -= tanks;
        }
    }

    @Override
    public int getTankCapacity(int tank) {
        for (IFluidHandler handler : handlers) {
            var tanks = handler.getTanks();
            if (tank < tanks) {
                return handler.getTankCapacity(tank);
            }
            tank -= tanks;
        }
        return 0;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        if (!filter.test(stack)) return false;
        for (IFluidHandler handler : handlers) {
            var tanks = handler.getTanks();
            if (tank < tanks) {
                return handler.isFluidValid(tank, stack);
            }
            tank -= tanks;
        }
        return false;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !filter.test(resource)) return 0;
        var copied = resource.copy();
        for (IFluidHandler handler : handlers) {
            var candidate = copied.copy();
            copied.shrink(handler.fill(candidate, action));
            if (copied.isEmpty()) break;
        }
        return resource.getAmount() - copied.getAmount();
    }

    @Override
    @NotNull
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !filter.test(resource)) return FluidStack.EMPTY;
        var copied = resource.copy();
        for (IFluidHandler handler : handlers) {
            var candidate = copied.copy();
            copied.shrink(handler.drain(candidate, action).getAmount());
            if (copied.isEmpty()) break;
        }
        copied.setAmount(resource.getAmount() - copied.getAmount());
        return copied;
    }

    @Override
    @NotNull
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (maxDrain == 0) return FluidStack.EMPTY;
        FluidStack totalDrained = null;
        for (IFluidHandler handler : handlers) {
            if (totalDrained == null || totalDrained.isEmpty()) {
                totalDrained = handler.drain(maxDrain, action);
                if (totalDrained.isEmpty()) totalDrained = null;
                else maxDrain -= totalDrained.getAmount();
            } else {
                FluidStack copy = totalDrained.copy();
                copy.setAmount(maxDrain);
                FluidStack drain = handler.drain(copy, action);
                totalDrained.grow(drain.getAmount());
                maxDrain -= drain.getAmount();
            }
            if (maxDrain <= 0) break;
        }
        return totalDrained == null ? FluidStack.EMPTY : totalDrained;
    }

    @Override
    public CompoundTag serializeNBT() {
        var tag = new CompoundTag();
        var list = new ListTag();
        for (IFluidHandler handler : handlers) {
            if (handler instanceof INBTSerializable<?> serializable) {
                list.add(serializable.serializeNBT());
            } else {
                GTCEu.LOGGER.warn("[FluidHandlerList] internal tank doesn't support serialization");
            }
        }
        tag.put("tanks", list);
        tag.putByte("type", list.getElementType());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        var list = nbt.getList("tanks", nbt.getByte("type"));
        for (int i = 0; i < list.size(); i++) {
            if (handlers[i] instanceof INBTSerializable serializable) {
                serializable.deserializeNBT(list.get(i));
            } else {
                GTCEu.LOGGER.warn("[FluidHandlerList] internal tank doesn't support serialization");
            }
        }
    }

    @Override
    public boolean supportsFill(int tank) {
        for (IFluidHandler handler : handlers) {
            var tanks = handler.getTanks();
            if (tank < tanks) {
                if (handler instanceof IFluidHandlerModifiable modifiable) {
                    return modifiable.supportsFill(tank);
                }
                return true;
            }
            tank -= tanks;
        }
        return true;
    }

    @Override
    public boolean supportsDrain(int tank) {
        for (IFluidHandler handler : handlers) {
            var tanks = handler.getTanks();
            if (tank < tanks) {
                if (handler instanceof IFluidHandlerModifiable modifiable) {
                    return modifiable.supportsDrain(tank);
                }
                return true;
            }
            tank -= tanks;
        }
        return true;
    }
}
