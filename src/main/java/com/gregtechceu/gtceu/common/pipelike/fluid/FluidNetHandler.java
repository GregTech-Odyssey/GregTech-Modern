package com.gregtechceu.gtceu.common.pipelike.fluid;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.common.blockentity.FluidPipeBlockEntity;
import com.gregtechceu.gtceu.common.cover.FluidFilterCover;
import com.gregtechceu.gtceu.common.cover.FluidRegulatorCover;
import com.gregtechceu.gtceu.common.cover.PumpCover;
import com.gregtechceu.gtceu.common.cover.data.FilterMode;

import net.minecraft.core.Direction;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public class FluidNetHandler implements IFluidHandlerModifiable {

    @Getter
    private FluidPipeNet net;
    private final FluidPipeBlockEntity pipe;
    @Getter
    private final Direction facing;
    private int simulatedTransfers = 0;

    public FluidNetHandler(FluidPipeNet net, FluidPipeBlockEntity pipe, Direction facing) {
        this.net = net;
        this.pipe = pipe;
        this.facing = facing;
    }

    public void updateNetwork(FluidPipeNet net) {
        this.net = net;
    }

    private void copyTransferred() {
        simulatedTransfers = pipe.getTransferredFluids();
    }

    public static boolean checkImportCover(CoverBehavior cover, boolean onPipe, FluidStack stack) {
        if (cover == null) return true;
        if (cover instanceof FluidFilterCover filter) {
            return (filter.getFilterMode() != FilterMode.FILTER_BOTH && (filter.getFilterMode() != FilterMode.FILTER_INSERT || !onPipe) && (filter.getFilterMode() != FilterMode.FILTER_EXTRACT || onPipe)) || filter.getFluidFilter().test(stack);
        }
        return true;
    }

    public int fillFirst(FluidStack stack, boolean simulate) {
        int amount = stack.getAmount();
        int total = 0;
        for (FluidRoutePath inv : net.getNetData(pipe.getPipePosLong(), pipe.getPipePos(), facing)) {
            if (pipe.autoTransfer && inv.getTargetPipe() == pipe && inv.getTargetFacing() != pipe.blockedSide) continue;
            int fill = fill(inv, stack, amount, simulate, false);
            amount -= fill;
            total += fill;
            if (amount <= 0) break;
        }
        return total;
    }

    public int fill(FluidRoutePath routePath, FluidStack stack, int amount, boolean simulate, boolean ignoreLimit) {
        int allowed = ignoreLimit ? amount : checkTransferable(routePath.getProperties().getThroughput(), amount, simulate);
        if (allowed == 0 || !routePath.matchesFilters(stack)) {
            return 0;
        }
        IFluidHandler neighbourHandler = routePath.getHandler(net.getLevel());
        if (neighbourHandler == null) return 0;

        // Check for FluidRegulatorCover at target pipe endpoint or destination tile
        CoverBehavior pipeCover = routePath.getTargetPipe().getCoverContainer().getCoverAtSide(routePath.getTargetFacing());
        CoverBehavior tileCover = getCoverOnPipeNeighbour(routePath.getTargetPipe(), routePath.getTargetFacing());
        if (pipeCover instanceof FluidRegulatorCover regulator && regulator.getIo() == IO.OUT) {
            return fillOverFluidRegulator(neighbourHandler, regulator, stack, amount, simulate, allowed, ignoreLimit);
        }
        if (tileCover instanceof FluidRegulatorCover regulator && regulator.getIo() == IO.IN) {
            return fillOverFluidRegulator(neighbourHandler, regulator, stack, amount, simulate, allowed, ignoreLimit);
        }

        return fill(neighbourHandler, stack.copy(), amount, simulate, allowed, ignoreLimit);
    }

    private int fill(IFluidHandler handler, FluidStack stack, int amount, boolean simulate, int allowed, boolean ignoreLimit) {
        if (amount == allowed) {
            stack.setAmount(amount);
            int r = handler.fill(stack, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
            if (!ignoreLimit) transfer(simulate, r);
            return r;
        }
        stack.setAmount(Math.min(allowed, amount));
        int r = handler.fill(stack, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
        if (!ignoreLimit) transfer(simulate, r);
        return r;
    }

    public CoverBehavior getCoverOnNeighbour(Direction handlerFacing) {
        ICoverable coverable = GTCapabilityHelper.getCoverable(pipe.getNeighbor(handlerFacing), handlerFacing.getOpposite());
        if (coverable == null) return null;
        return coverable.getCoverAtSide(handlerFacing.getOpposite());
    }

    private CoverBehavior getCoverOnPipeNeighbour(FluidPipeBlockEntity targetPipe, Direction targetFacing) {
        ICoverable coverable = GTCapabilityHelper.getCoverable(targetPipe.getNeighbor(targetFacing), targetFacing.getOpposite());
        if (coverable == null) return null;
        return coverable.getCoverAtSide(targetFacing.getOpposite());
    }

    public static int countFluid(IFluidHandler handler, FluidStack stack) {
        int count = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack inTank = handler.getFluidInTank(i);
            if (!inTank.isEmpty() && inTank.isFluidEqual(stack)) {
                count += inTank.getAmount();
            }
        }
        return count;
    }

    private int fillOverFluidRegulator(IFluidHandler handler, FluidRegulatorCover regulator, FluidStack stack, int amount, boolean simulate, int allowed, boolean ignoreLimit) {
        switch (regulator.getTransferMode()) {
            case KEEP_EXACT:
                int rate = regulator.getFilteredFluidAmount(stack);
                if (rate <= 0) return 0;
                int current = countFluid(handler, stack);
                int deficit = rate - current;
                if (deficit <= 0) return 0;
                int toFill = Math.min(allowed, Math.min(amount, deficit));
                return fill(handler, stack.copy(), toFill, simulate, toFill, ignoreLimit);
            case TRANSFER_EXACT:
                int exactAmount = regulator.getFilteredFluidAmount(stack);
                if (exactAmount <= 0) return 0;
                int exact = Math.min(allowed, Math.min(amount, exactAmount));
                return fill(handler, stack.copy(), exact, simulate, exact, ignoreLimit);
            default:
                return fill(handler, stack.copy(), amount, simulate, allowed, ignoreLimit);
        }
    }

    private int checkTransferable(int rate, int amount, boolean simulate) {
        int max = rate * 20;
        if (simulate) return Math.max(0, Math.min(max - simulatedTransfers, amount));
        else return Math.max(0, Math.min(max - pipe.getTransferredFluids(), amount));
    }

    private void transfer(boolean simulate, int amount) {
        if (simulate) simulatedTransfers += amount;
        else pipe.addTransferredFluids(amount);
    }

    @Override
    public void setFluidInTank(int tank, FluidStack stack) {}

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int i) {
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int i) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int i, @NotNull FluidStack fluidStack) {
        return true;
    }

    @Override
    public int fill(FluidStack stack, FluidAction fluidAction) {
        if (stack.isEmpty()) return 0;
        if (net == null || pipe == null || pipe.isInValid() || pipe.isBlocked(facing)) {
            return 0;
        }
        copyTransferred();
        CoverBehavior pipeCover = pipe.getCoverContainer().getCoverAtSide(facing);
        CoverBehavior tileCover = getCoverOnNeighbour(facing);
        boolean pipePump = pipeCover instanceof PumpCover;
        boolean tilePump = tileCover instanceof PumpCover;
        // abort if there are two pump
        if (pipePump && tilePump) return 0;
        if (tileCover != null && !checkImportCover(tileCover, false, stack)) return 0;
        boolean simulate = fluidAction.simulate();
        return fillFirst(stack, simulate);
    }

    @Override
    public @NotNull FluidStack drain(FluidStack fluidStack, FluidAction fluidAction) {
        return FluidStack.EMPTY;
    }

    @Override
    public @NotNull FluidStack drain(int i, FluidAction fluidAction) {
        return FluidStack.EMPTY;
    }
}
