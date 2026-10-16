package com.gregtechceu.gtceu.common.pipelike.cable;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.common.blockentity.CableBlockEntity;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;

import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongHeapPriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 从源导线出发按「线损最小、其次格数最少」求到每个接收面的最短路线（Dijkstra），结果由 {@link EnergyNet} 按源导线缓存。
 */
public final class EnergyNetWalker {

    private static final int INDEX_BITS = 20;
    private static final int MAX_NODES = 1 << INDEX_BITS;
    private static final int HOP_BITS = 20;
    private static final long MAX_LOSS = (1L << (63 - INDEX_BITS - HOP_BITS)) - 1;

    private final ObjectArrayList<CableBlockEntity> nodes = new ObjectArrayList<>();
    private final Reference2IntOpenHashMap<CableBlockEntity> index = new Reference2IntOpenHashMap<>();
    private final LongArrayList loss = new LongArrayList();
    private final IntArrayList hops = new IntArrayList();
    private final IntArrayList parent = new IntArrayList();
    private final LongArrayList minVoltage = new LongArrayList();
    private final BooleanArrayList settled = new BooleanArrayList();
    private final LongHeapPriorityQueue queue = new LongHeapPriorityQueue();
    private final List<EnergyRoutePath> routes = new ArrayList<>();

    private EnergyNetWalker() {
        index.defaultReturnValue(-1);
    }

    @Nullable
    public static List<EnergyRoutePath> createNetData(EnergyNet pipeNet, BlockPos sourcePipe) {
        try {
            if (!(pipeNet.getLevel().getBlockEntity(sourcePipe) instanceof CableBlockEntity source)) return null;
            var walker = new EnergyNetWalker();
            walker.walk(source);
            return walker.routes;
        } catch (Exception e) {
            GTCEu.LOGGER.error("error while create net data for energynet", e);
        }
        return null;
    }

    private void walk(CableBlockEntity source) {
        visit(source, source.getNodeData().getLossPerBlock(), 1, -1, source.getNodeData().getVoltage());
        while (!queue.isEmpty()) {
            int i = (int) (queue.dequeueLong() & (MAX_NODES - 1));
            if (settled.getBoolean(i)) continue;
            settled.set(i, true);
            expand(i);
        }
    }

    private void expand(int i) {
        CableBlockEntity pipe = nodes.get(i);
        CableBlockEntity[] path = null;
        for (Direction side : GTUtil.DIRECTIONS) {
            if (!pipe.isConnected(side)) continue;
            BlockEntity tile = pipe.getNeighborBlockEntity(side);
            if (tile instanceof CableBlockEntity next) {
                Direction back = side.getOpposite();
                if (next.isConnected(back) && !next.isBlocked(back)) relax(i, next);
                continue;
            }
            if (GTCapabilityHelper.getEnergyContainer(tile, side.getOpposite()) == null) continue;
            if (path == null) path = pathTo(i);
            routes.add(new EnergyRoutePath(pipe.getPipePos(), side, path, hops.getInt(i), loss.getLong(i), minVoltage.getLong(i)));
        }
    }

    private void relax(int from, CableBlockEntity next) {
        long nextLoss = Math.min(MAX_LOSS, loss.getLong(from) + next.getNodeData().getLossPerBlock());
        int nextHops = hops.getInt(from) + 1;
        long nextVoltage = Math.min(minVoltage.getLong(from), next.getNodeData().getVoltage());
        int j = index.getInt(next);
        if (j < 0) {
            visit(next, nextLoss, nextHops, from, nextVoltage);
            return;
        }
        if (settled.getBoolean(j)) return;
        long known = loss.getLong(j);
        if (nextLoss > known || (nextLoss == known && nextHops >= hops.getInt(j))) return;
        loss.set(j, nextLoss);
        hops.set(j, nextHops);
        parent.set(j, from);
        minVoltage.set(j, nextVoltage);
        queue.enqueue(key(nextLoss, nextHops, j));
    }

    private void visit(CableBlockEntity pipe, long pipeLoss, int pipeHops, int from, long voltage) {
        int j = nodes.size();
        if (j >= MAX_NODES || pipeHops >= 1 << HOP_BITS) return;
        nodes.add(pipe);
        index.put(pipe, j);
        loss.add(pipeLoss);
        hops.add(pipeHops);
        parent.add(from);
        minVoltage.add(voltage);
        settled.add(false);
        queue.enqueue(key(pipeLoss, pipeHops, j));
    }

    private CableBlockEntity[] pathTo(int i) {
        CableBlockEntity[] path = new CableBlockEntity[hops.getInt(i)];
        for (int k = path.length - 1, n = i; k >= 0; k--, n = parent.getInt(n)) path[k] = nodes.get(n);
        return path;
    }

    private static long key(long pathLoss, int pathHops, int node) {
        return (pathLoss << (HOP_BITS + INDEX_BITS)) | ((long) pathHops << INDEX_BITS) | node;
    }
}
