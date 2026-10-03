package com.gregtechceu.gtceu.common.pipelike.item;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.cover.filter.SimpleItemFilter;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.key.IKeyHandler;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.common.blockentity.ItemPipeBlockEntity;
import com.gregtechceu.gtceu.common.cover.ConveyorCover;
import com.gregtechceu.gtceu.common.cover.ItemFilterCover;
import com.gregtechceu.gtceu.common.cover.RobotArmCover;
import com.gregtechceu.gtceu.common.cover.data.DistributionMode;
import com.gregtechceu.gtceu.common.cover.data.FilterMode;
import com.gregtechceu.gtceu.utils.FacingPos;

import net.minecraft.core.Direction;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import com.gto.fastcollection.fastutil.O2IOpenCacheHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public final class ItemNetHandler implements IKeyHandler<AEItemKey> {

    @Getter
    private ItemPipeNet net;
    private final ItemPipeBlockEntity pipe;
    @Getter
    private final Direction facing;
    private final O2IOpenCacheHashMap<FacingPos> simulatedTransfersGlobalRoundRobin = new O2IOpenCacheHashMap<>();
    private int simulatedTransfers = 0;
    private final KeyInventory<AEItemKey> testHandler = KeyInventory.items(1, Long.MAX_VALUE, false);

    public ItemNetHandler(ItemPipeNet net, ItemPipeBlockEntity pipe, Direction facing) {
        this.net = net;
        this.pipe = pipe;
        this.facing = facing;
    }

    public void updateNetwork(ItemPipeNet net) {
        this.net = net;
    }

    private void copyTransferred() {
        simulatedTransfers = pipe.getTransferredItems();
        simulatedTransfersGlobalRoundRobin.clear();
        simulatedTransfersGlobalRoundRobin.putAll(pipe.getTransferred());
    }

    @Override
    public long insert(AEItemKey key, long amount, boolean simulate) {
        if (amount <= 0 || pipe == null) return 0;
        pipe.checkNetwork();
        if (net == null || pipe.isInValid() || pipe.isBlocked(facing)) {
            return 0;
        }
        copyTransferred();
        CoverBehavior pipeCover = pipe.getCoverContainer().getCoverAtSide(facing);
        CoverBehavior tileCover = getCoverOnNeighbour(facing);
        boolean pipeConveyor = pipeCover instanceof ConveyorCover;
        boolean tileConveyor = tileCover instanceof ConveyorCover;
        // abort if there are two conveyors
        if (pipeConveyor && tileConveyor) return 0;
        if (tileCover != null && !checkImportCover(tileCover, false, key)) return 0;
        if (!pipeConveyor && !tileConveyor) return insertFirst(key, amount, simulate);
        ConveyorCover conveyor = (ConveyorCover) (pipeConveyor ? pipeCover : tileCover);
        if (conveyor.getIo() == (pipeConveyor ? IO.IN : IO.OUT)) {
            boolean roundRobinGlobal = conveyor.getDistributionMode() == DistributionMode.ROUND_ROBIN_GLOBAL;
            if (roundRobinGlobal || conveyor.getDistributionMode() == DistributionMode.ROUND_ROBIN_PRIO) return insertRoundRobin(key, amount, simulate, roundRobinGlobal);
        }
        return insertFirst(key, amount, simulate);
    }

    public static boolean checkImportCover(CoverBehavior cover, boolean onPipe, AEItemKey key) {
        if (cover == null) return true;
        if (cover instanceof ItemFilterCover filter) {
            return (filter.getFilterMode() != FilterMode.FILTER_BOTH && (filter.getFilterMode() != FilterMode.FILTER_INSERT || !onPipe) && (filter.getFilterMode() != FilterMode.FILTER_EXTRACT || onPipe)) || filter.getItemFilter().test(Keys.displayStack(key));
        }
        return true;
    }

    public long insertFirst(AEItemKey key, long amount, boolean simulate) {
        long left = amount;
        for (var inv : net.getNetData(pipe.getPipeLongPos(), pipe.getPipePos(), facing).array) {
            if (pipe.autoTransfer && inv.getTargetPipe() == pipe && inv.getTargetFacing() != pipe.blockedSide) continue;
            left -= insert(inv, key, left, simulate);
            if (left <= 0) return amount;
        }
        return amount - left;
    }

    public long insertRoundRobin(AEItemKey key, long amount, boolean simulate, boolean global) {
        var routePaths = net.getNetData(pipe.getPipeLongPos(), pipe.getPipePos(), facing);
        if (routePaths.size == 0) return 0;
        if (routePaths.size == 1) return insert(routePaths.array[0], key, amount, simulate);
        if (global) {
            return insertToHandlersEnhanced(routePaths.array, key, amount, simulate);
        }
        var routePathsCopy = new ObjectArrayList<>(routePaths.array);
        long inserted = insertToHandlers(routePathsCopy, key, amount, simulate);
        if (inserted < amount && !routePathsCopy.isEmpty()) inserted += insertToHandlers(routePathsCopy, key, amount - inserted, simulate);
        return inserted;
    }

    /**
     * Inserts items equally to all handlers
     * if it couldn't insert all items, the handler will be removed
     *
     * @param copy     to insert to
     * @param simulate simulate
     */
    private long insertToHandlers(List<ItemRoutePath> copy, AEItemKey key, long count, boolean simulate) {
        Iterator<ItemRoutePath> routePathIterator = copy.listIterator(0);
        long inserted = 0;
        long c = count / copy.size();
        long m = c == 0 ? count % copy.size() : 0;
        while (routePathIterator.hasNext()) {
            ItemRoutePath routePath = routePathIterator.next();
            long amount = c;
            if (m > 0) {
                amount++;
                m--;
            }
            amount = Math.min(amount, count - inserted);
            if (amount == 0) break;
            long ins = insert(routePath, key, amount, simulate);
            long r = amount - ins;
            inserted += ins;
            if (r == 1 && c == 0 && amount == 1) {
                m++;
            }
            if (r > 0) routePathIterator.remove();
        }
        return inserted;
    }

    private long insertToHandlersEnhanced(ItemRoutePath[] array, AEItemKey key, long total, boolean simulate) {
        List<EnhancedRoundRobinData> transferred = new ArrayList<>();
        IntList steps = new IntArrayList();
        int min = Integer.MAX_VALUE;
        int count = Keys.saturatedInt(total);
        // find inventories that are not full and get the amount that was inserted in total
        for (var inv : array) {
            int ins = (int) insert(inv, key, count, true, true);
            if (ins <= 0) continue;
            int didTransfer = didTransferTo(inv, simulate);
            EnhancedRoundRobinData data = new EnhancedRoundRobinData(inv, ins, didTransfer);
            transferred.add(data);
            min = Math.min(min, didTransfer);
            if (!steps.contains(didTransfer)) {
                steps.add(didTransfer);
            }
        }
        if (transferred.isEmpty() || steps.isEmpty()) return 0;
        if (!simulate && min < Integer.MAX_VALUE) {
            decrementBy(min);
        }
        transferred.sort(Comparator.comparingInt(data -> data.transferred));
        steps.sort(Integer::compare);
        if (transferred.getFirst().transferred != steps.getInt(0)) {
            return 0;
        }
        int amount = count;
        int c = amount / transferred.size();
        int m = amount % transferred.size();
        List<EnhancedRoundRobinData> transferredCopy = new ArrayList<>(transferred);
        int nextStep = steps.removeInt(0);
        // equally distribute items over all inventories
        // it takes into account how much was inserted in total
        // f.e. if inv1 has 2 inserted and inv2 has 6 inserted, it will first try to insert 4 into inv1 so that both
        // have 6 and then it will distribute the rest equally
        outer:
        while (amount > 0 && !transferredCopy.isEmpty()) {
            Iterator<EnhancedRoundRobinData> iterator = transferredCopy.listIterator(0);
            while (iterator.hasNext()) {
                EnhancedRoundRobinData data = iterator.next();
                if (nextStep >= 0 && data.transferred >= nextStep) break;
                int toInsert;
                if (nextStep <= 0) {
                    if (amount <= m) {
                        // break outer;
                        toInsert = 1;
                    } else {
                        toInsert = Math.min(c, amount);
                    }
                } else {
                    toInsert = Math.min(amount, nextStep - data.transferred);
                }
                if (data.toTransfer + toInsert >= data.maxInsertable) {
                    data.toTransfer = data.maxInsertable;
                    iterator.remove();
                } else {
                    data.toTransfer += toInsert;
                }
                data.transferred += toInsert;
                if ((amount -= toInsert) == 0) {
                    break outer;
                }
            }
            for (EnhancedRoundRobinData data : transferredCopy) {
                if (data.transferred < nextStep) continue outer;
            }
            if (steps.isEmpty()) {
                if (nextStep >= 0) {
                    c = amount / transferredCopy.size();
                    m = amount % transferredCopy.size();
                    nextStep = -1;
                }
            } else {
                nextStep = steps.removeInt(0);
            }
        }
        long inserted = 0;
        // finally actually insert the item
        for (EnhancedRoundRobinData data : transferred) {
            int ins = (int) insert(data.routePath, key, data.toTransfer, simulate);
            inserted += ins;
            transferTo(data.routePath, simulate, ins);
        }
        return inserted;
    }

    public long insert(ItemRoutePath handler, AEItemKey key, long amount, boolean simulate) {
        return insert(handler, key, amount, simulate, false);
    }

    public long insert(ItemRoutePath routePath, AEItemKey key, long amount, boolean simulate, boolean ignoreLimit) {
        if (amount <= 0) return 0;
        long allowed = ignoreLimit ? amount : checkTransferable(routePath.getProperties().getTransferRate(), amount, simulate);
        if (allowed == 0 || !routePath.matchesFilters(key)) {
            return 0;
        }
        CoverBehavior pipeCover = routePath.getTargetPipe().getCoverContainer().getCoverAtSide(routePath.getTargetFacing());
        CoverBehavior tileCover = getCoverOnNeighbour(routePath.getTargetFacing());
        if (pipeCover != null) {
            testHandler.set(0, key, amount);
            var itemHandler = pipeCover.getItemHandlerCap(testHandler);
            if (itemHandler == null || (itemHandler != testHandler && (allowed = itemHandler.extract(0, key, allowed, true)) <= 0)) {
                testHandler.set(0, null, 0);
                return 0;
            }
            testHandler.set(0, null, 0);
        }
        IKeyHandler<AEItemKey> neighbourHandler = routePath.getHandler(net.getLevel());
        if (neighbourHandler == null) return 0;
        if (pipeCover instanceof RobotArmCover robotArm && robotArm.getIo() == IO.OUT) {
            return insertOverRobotArm(neighbourHandler, robotArm, key, amount, simulate, allowed, ignoreLimit);
        }
        if (tileCover instanceof RobotArmCover robotArm && robotArm.getIo() == IO.IN) {
            return insertOverRobotArm(neighbourHandler, robotArm, key, amount, simulate, allowed, ignoreLimit);
        }
        return insert(neighbourHandler, key, amount, simulate, allowed, ignoreLimit);
    }

    private long insert(IKeyHandler<AEItemKey> handler, AEItemKey key, long amount, boolean simulate, long allowed, boolean ignoreLimit) {
        long ins = handler.insert(key, Math.min(allowed, amount), simulate);
        if (!ignoreLimit) transfer(simulate, Keys.saturatedInt(ins));
        return ins;
    }

    @Nullable
    public CoverBehavior getCoverOnNeighbour(Direction handlerFacing) {
        ICoverable coverable = GTCapabilityHelper.getCoverable(pipe.getNeighborBlockEntity(handlerFacing), handlerFacing.getOpposite());
        if (coverable == null) return null;
        return coverable.getCoverAtSide(handlerFacing.getOpposite());
    }

    public long insertOverRobotArm(IKeyHandler<AEItemKey> handler, RobotArmCover arm, AEItemKey key, long amount, boolean simulate, long allowed, boolean ignoreLimit) {
        int rate = arm.getFilterHandler().getFilter().testItemCount(Keys.displayStack(key));
        long count;
        switch (arm.getTransferMode()) {
            case TRANSFER_ANY:
                return insert(handler, key, amount, simulate, allowed, ignoreLimit);
            case KEEP_EXACT:
                if (arm.getFilterHandler().getFilter().supportsAmounts()) {
                    count = rate - countStack(handler, key, arm);
                } else {
                    count = rate;
                }
                if (count <= 0) return 0;
                count = Math.min(allowed, Math.min(amount, count));
                return insert(handler, key, amount, simulate, count, ignoreLimit);
            case TRANSFER_EXACT:
                long max = allowed + arm.getBuffer();
                count = Math.min(max, Math.min(rate, amount));
                if (count < rate) {
                    arm.buffer(Keys.saturatedInt(allowed));
                    return 0;
                } else {
                    arm.clearBuffer();
                }
                if (insert(handler, key, amount, true, count, ignoreLimit) != count) {
                    return 0;
                }
                return insert(handler, key, amount, simulate, count, ignoreLimit);
        }
        return 0;
    }

    public static long countStack(IKeyHandler<AEItemKey> handler, AEItemKey key, RobotArmCover arm) {
        if (arm == null) return 0;
        long count = 0;
        ItemFilter filter = arm.getFilterHandler().getFilter();
        boolean ignoreNBT = filter instanceof SimpleItemFilter simple && simple.isIgnoreNbt();
        int size = handler.size();
        for (int i = 0; i < size; i++) {
            long stored = handler.amountAt(i);
            if (stored <= 0) continue;
            AEItemKey slot = handler.keyAt(i);
            if (slot == null) continue;
            if (ignoreNBT && slot.getItem() != key.getItem()) continue;
            else if (slot != key) continue;
            if (filter.test(Keys.displayStack(slot))) {
                count += stored;
            }
        }
        return count;
    }

    private int checkTransferable(float rate, long amount, boolean simulate) {
        int max = (int) ((rate * 64) + 0.5);
        if (simulate) return (int) Math.max(0, Math.min(max - simulatedTransfers, amount));
        else return (int) Math.max(0, Math.min(max - pipe.getTransferredItems(), amount));
    }

    private void transfer(boolean simulate, int amount) {
        if (simulate) simulatedTransfers += amount;
        else pipe.addTransferredItems(amount);
    }

    @Override
    public AEKeyType keyType() {
        return AEKeyType.items();
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public @Nullable AEItemKey keyAt(int slot) {
        return null;
    }

    @Override
    public long amountAt(int slot) {
        return 0;
    }

    @Override
    public long slotLimit(int slot) {
        return 64;
    }

    @Override
    public long insert(int slot, AEItemKey key, long amount, boolean simulate) {
        return insert(key, amount, simulate);
    }

    @Override
    public long extract(int slot, AEItemKey key, long amount, boolean simulate) {
        return 0;
    }

    @Override
    public long extract(AEItemKey key, long amount, boolean simulate) {
        return 0;
    }

    @Override
    public long spaceFor(int slot, AEItemKey key) {
        return 64;
    }

    @Override
    public long count(AEItemKey key) {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    private void transferTo(ItemRoutePath handler, boolean simulate, int amount) {
        if (simulate) {
            simulatedTransfersGlobalRoundRobin.addTo(handler.toFacingPos(), amount);
        } else {
            pipe.getTransferred().addTo(handler.toFacingPos(), amount);
        }
    }

    private int didTransferTo(ItemRoutePath handler, boolean simulate) {
        if (simulate) {
            return simulatedTransfersGlobalRoundRobin.getOrDefault(handler.toFacingPos(), 0);
        } else {
            return pipe.getTransferred().getOrDefault(handler.toFacingPos(), 0);
        }
    }

    private void decrementBy(int amount) {
        for (ObjectIterator<Object2IntMap.Entry<FacingPos>> it = pipe.getTransferred().object2IntEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            entry.setValue(entry.getIntValue() - amount);
        }
    }

    private static class EnhancedRoundRobinData {

        private final ItemRoutePath routePath;
        private final int maxInsertable;
        private int transferred;
        private int toTransfer = 0;

        private EnhancedRoundRobinData(ItemRoutePath routePath, int maxInsertable, int transferred) {
            this.maxInsertable = maxInsertable;
            this.transferred = transferred;
            this.routePath = routePath;
        }
    }
}
