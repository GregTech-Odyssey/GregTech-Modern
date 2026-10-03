package com.gregtechceu.gtceu.common.pipelike.fluid;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.common.blockentity.FluidPipeBlockEntity;
import com.gregtechceu.gtceu.common.cover.FluidFilterCover;
import com.gregtechceu.gtceu.common.cover.FluidRegulatorCover;
import com.gregtechceu.gtceu.common.cover.PumpCover;
import com.gregtechceu.gtceu.common.cover.data.FilterMode;

import net.minecraft.core.Direction;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

public final class FluidNetHandler implements IKeyHandler<AEFluidKey> {

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

    public static boolean checkImportCover(CoverBehavior cover, boolean onPipe, AEFluidKey key) {
        if (cover == null) return true;
        if (cover instanceof FluidFilterCover filter) {
            return (filter.getFilterMode() != FilterMode.FILTER_BOTH && (filter.getFilterMode() != FilterMode.FILTER_INSERT || !onPipe) && (filter.getFilterMode() != FilterMode.FILTER_EXTRACT || onPipe)) || filter.getFluidFilter().test(Keys.displayFluid(key));
        }
        return true;
    }

    public long fillFirst(AEFluidKey key, long amount, boolean simulate) {
        long total = 0;
        var data = net.getNetData(pipe.getPipeLongPos(), pipe.getPipePos(), facing);
        if (simulate) {
            for (var inv : data.array) {
                if (pipe.autoTransfer && inv.getTargetPipe() == pipe && inv.getTargetFacing() != pipe.blockedSide) continue;
                long fill = fill(inv, key, amount, simulate, false);
                amount -= fill;
                total += fill;
                if (amount <= 0) break;
            }
        } else {
            for (var inv : data) {
                if (pipe.autoTransfer && inv.getTargetPipe() == pipe && inv.getTargetFacing() != pipe.blockedSide)
                    continue;
                long fill = fill(inv, key, amount, simulate, false);
                amount -= fill;
                total += fill;
                if (amount <= 0) break;
            }
        }
        return total;
    }

    public long fill(FluidRoutePath routePath, AEFluidKey key, long amount, boolean simulate, boolean ignoreLimit) {
        long allowed = ignoreLimit ? amount : checkTransferable(routePath.getProperties().getThroughput(), amount, simulate);
        if (allowed == 0 || !routePath.matchesFilters(key)) {
            return 0;
        }
        IKeyHandler<AEFluidKey> neighbourHandler = routePath.getHandler(net.getLevel());
        if (neighbourHandler == null) return 0;

        // Check for FluidRegulatorCover at target pipe endpoint or destination tile
        CoverBehavior pipeCover = routePath.getTargetPipe().getCoverContainer().getCoverAtSide(routePath.getTargetFacing());
        CoverBehavior tileCover = getCoverOnPipeNeighbour(routePath.getTargetPipe(), routePath.getTargetFacing());
        if (pipeCover instanceof FluidRegulatorCover regulator && regulator.getIo() == IO.OUT) {
            return fillOverFluidRegulator(neighbourHandler, regulator, key, amount, simulate, allowed, ignoreLimit);
        }
        if (tileCover instanceof FluidRegulatorCover regulator && regulator.getIo() == IO.IN) {
            return fillOverFluidRegulator(neighbourHandler, regulator, key, amount, simulate, allowed, ignoreLimit);
        }

        return fill(neighbourHandler, key, amount, simulate, allowed, ignoreLimit);
    }

    private long fill(IKeyHandler<AEFluidKey> handler, AEFluidKey key, long amount, boolean simulate, long allowed, boolean ignoreLimit) {
        long r = handler.insert(key, Math.min(allowed, amount), simulate);
        if (!ignoreLimit) transfer(simulate, Keys.saturatedInt(r));
        return r;
    }

    @Nullable
    public CoverBehavior getCoverOnNeighbour(Direction handlerFacing) {
        ICoverable coverable = GTCapabilityHelper.getCoverable(pipe.getNeighborBlockEntity(handlerFacing), handlerFacing.getOpposite());
        if (coverable == null) return null;
        return coverable.getCoverAtSide(handlerFacing.getOpposite());
    }

    @Nullable
    private CoverBehavior getCoverOnPipeNeighbour(FluidPipeBlockEntity targetPipe, Direction targetFacing) {
        ICoverable coverable = GTCapabilityHelper.getCoverable(targetPipe.getNeighborBlockEntity(targetFacing), targetFacing.getOpposite());
        if (coverable == null) return null;
        return coverable.getCoverAtSide(targetFacing.getOpposite());
    }

    public static long countFluid(IKeyHandler<AEFluidKey> handler, AEFluidKey key) {
        long count = 0;
        int size = handler.size();
        for (int i = 0; i < size; i++) {
            long stored = handler.amountAt(i);
            if (stored > 0 && handler.keyAt(i) == key) {
                count += stored;
            }
        }
        return count;
    }

    private long fillOverFluidRegulator(IKeyHandler<AEFluidKey> handler, FluidRegulatorCover regulator, AEFluidKey key, long amount, boolean simulate, long allowed, boolean ignoreLimit) {
        switch (regulator.getTransferMode()) {
            case KEEP_EXACT:
                int rate = regulator.getFilteredFluidAmount(Keys.displayFluid(key));
                if (rate <= 0) return 0;
                long current = countFluid(handler, key);
                long deficit = rate - current;
                if (deficit <= 0) return 0;
                long toFill = Math.min(allowed, Math.min(amount, deficit));
                return fill(handler, key, toFill, simulate, toFill, ignoreLimit);
            case TRANSFER_EXACT:
                int exactAmount = regulator.getFilteredFluidAmount(Keys.displayFluid(key));
                if (exactAmount <= 0) return 0;
                long exact = Math.min(allowed, Math.min(amount, exactAmount));
                return fill(handler, key, exact, simulate, exact, ignoreLimit);
            default:
                return fill(handler, key, amount, simulate, allowed, ignoreLimit);
        }
    }

    private int checkTransferable(int rate, long amount, boolean simulate) {
        int max = rate * 20;
        if (simulate) return (int) Math.max(0, Math.min(max - simulatedTransfers, amount));
        else return (int) Math.max(0, Math.min(max - pipe.getTransferredFluids(), amount));
    }

    private void transfer(boolean simulate, int amount) {
        if (simulate) simulatedTransfers += amount;
        else pipe.addTransferredFluids(amount);
    }

    @Override
    public AEKeyType keyType() {
        return AEKeyType.fluids();
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public @Nullable AEFluidKey keyAt(int slot) {
        return null;
    }

    @Override
    public long amountAt(int slot) {
        return 0;
    }

    @Override
    public long slotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public long spaceFor(int slot, AEFluidKey key) {
        return Integer.MAX_VALUE;
    }

    @Override
    public long count(AEFluidKey key) {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public long insert(int slot, AEFluidKey key, long amount, boolean simulate) {
        return insert(key, amount, simulate);
    }

    @Override
    public long insert(AEFluidKey key, long amount, boolean simulate) {
        if (amount <= 0 || pipe == null) return 0;
        pipe.checkNetwork();
        if (net == null || pipe.isInValid() || pipe.isBlocked(facing)) {
            return 0;
        }
        copyTransferred();
        CoverBehavior pipeCover = pipe.getCoverContainer().getCoverAtSide(facing);
        CoverBehavior tileCover = getCoverOnNeighbour(facing);
        boolean pipePump = pipeCover instanceof PumpCover;
        boolean tilePump = tileCover instanceof PumpCover;
        // abort if there are two pump
        if (pipePump && tilePump) return 0;
        if (tileCover != null && !checkImportCover(tileCover, false, key)) return 0;
        return fillFirst(key, amount, simulate);
    }

    @Override
    public long extract(int slot, AEFluidKey key, long amount, boolean simulate) {
        return 0;
    }

    @Override
    public long extract(AEFluidKey key, long amount, boolean simulate) {
        return 0;
    }
}
