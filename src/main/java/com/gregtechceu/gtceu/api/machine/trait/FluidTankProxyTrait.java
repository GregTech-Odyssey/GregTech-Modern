package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.fluid.ICustomFluidStackHandler;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.Direction;
import net.minecraftforge.fluids.FluidStack;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

@Getter
public class FluidTankProxyTrait extends MachineTrait implements ICustomFluidStackHandler, ICapabilityTrait {

    public final IO capabilityIO;
    public ICustomFluidStackHandler proxy;
    @Setter
    @Getter
    protected Predicate<@Nullable Direction> capabilityValidator = GTUtil.FAVORABLE;

    public FluidTankProxyTrait(MetaMachine machine, IO capabilityIO) {
        super(machine);
        this.capabilityIO = capabilityIO;
    }

    @Override
    public int getTanks() {
        return proxy == null ? 0 : proxy.getTanks();
    }

    @NotNull
    @Override
    public FluidStack getFluidInTank(int tank) {
        return proxy == null ? FluidStack.EMPTY : proxy.getFluidInTank(tank);
    }

    @Override
    public void setFluidInTank(int tank, @NotNull FluidStack fluidStack) {
        if (proxy != null) {
            proxy.setFluidInTank(tank, fluidStack);
        }
    }

    @Override
    public int getTankCapacity(int tank) {
        return proxy == null ? 0 : proxy.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return proxy != null && proxy.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (proxy != null && canCapInput()) {
            return proxy.fill(resource, action);
        }
        return 0;
    }

    public int fillInternal(FluidStack resource, FluidAction action) {
        if (proxy != null && !resource.isEmpty()) {
            return proxy.fill(resource, action);
        }
        return 0;
    }

    public FluidStack drainInternal(FluidStack resource, FluidAction action) {
        if (proxy != null && !resource.isEmpty()) {
            return proxy.drain(resource, action);
        }
        return FluidStack.EMPTY;
    }

    @NotNull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (proxy != null && canCapOutput()) {
            return proxy.drain(maxDrain, action);
        }
        return FluidStack.EMPTY;
    }

    @NotNull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (proxy != null && canCapOutput()) {
            return proxy.drain(resource, action);
        }
        return FluidStack.EMPTY;
    }

    public FluidStack drainInternal(int maxDrain, FluidAction action) {
        return proxy == null ? FluidStack.EMPTY : proxy.drain(maxDrain, action);
    }

    public boolean isEmpty() {
        if (proxy instanceof NotifiableFluidTank fluidTank) return fluidTank.isEmpty();
        boolean isEmpty = true;
        if (proxy != null) {
            for (int i = 0; i < proxy.getTanks(); i++) {
                if (!proxy.getFluidInTank(i).isEmpty()) {
                    isEmpty = false;
                    break;
                }
            }
        }
        return isEmpty;
    }

    public void exportToNearby(Direction... facings) {
        if (isEmpty()) return;
        var level = getMachine().getLevel();
        var pos = getMachine().getPos();
        for (Direction facing : facings) {
            var filter = getMachine().getFluidCapFilter(facing, IO.OUT);
            machine.holder.blockEntityDirectionCache.getAdjacentFluidHandler(level, pos, facing).ifPresent(adj -> GTTransferUtils.transferFluidsFiltered(this, adj, filter));
        }
    }

    /**
     * @return {@code this}.
     */
    public FluidTankProxyTrait setProxy(final ICustomFluidStackHandler proxy) {
        this.proxy = proxy;
        return this;
    }
}
