package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.PredicateDirections;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Layout {

    public record Cell(int x, int y, int z, TraceabilityPredicate predicate, int layer, int node) {}

    private static final int REACH = 2;
    private static final double HORIZONTAL_BONUS = 1000;

    private final List<Cell> cells;
    private final LongOpenHashSet positions;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final int[] outward;
    private final RelativeDirection[] required;
    @Nullable
    private final boolean[] excludedNodes;
    @Nullable
    private final boolean[] excluded;
    @Nullable
    private final Layout whole;
    @Nullable
    private final Structure source;

    private Layout(Layout base, boolean[] nodes) {
        this.whole = base.whole != null ? base.whole : base;
        this.source = base.source;
        this.cells = base.cells;
        this.positions = base.positions;
        this.minX = base.minX;
        this.minY = base.minY;
        this.minZ = base.minZ;
        this.maxX = base.maxX;
        this.maxY = base.maxY;
        this.maxZ = base.maxZ;
        this.outward = base.outward;
        this.required = base.required;
        this.excludedNodes = nodes.clone();
        this.excluded = new boolean[cells.size()];
        for (int i = 0; i < excluded.length; i++) {
            int node = cells.get(i).node;
            excluded[i] = node >= 0 && node < nodes.length && nodes[node];
        }
    }

    Layout(List<Cell> cells, @Nullable Structure source) {
        this.source = source;
        this.whole = null;
        this.excludedNodes = null;
        this.excluded = null;
        this.cells = cells;
        this.positions = new LongOpenHashSet(cells.size());
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (var cell : cells) {
            positions.add(BlockPos.asLong(cell.x, cell.y, cell.z));
            minX = Math.min(minX, cell.x);
            minY = Math.min(minY, cell.y);
            minZ = Math.min(minZ, cell.z);
            maxX = Math.max(maxX, cell.x);
            maxY = Math.max(maxY, cell.y);
            maxZ = Math.max(maxZ, cell.z);
        }
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.outward = new int[cells.size()];
        this.required = new RelativeDirection[cells.size()];
        for (int i = 0; i < required.length; i++) {
            for (var simple : cells.get(i).predicate().common) {
                if (simple instanceof PredicateDirections directions && directions.getDirections().length > 0) {
                    required[i] = directions.getDirections()[0];
                    break;
                }
            }
        }
        double cx = (minX + maxX) / 2.0, cy = (minY + maxY) / 2.0, cz = (minZ + maxZ) / 2.0;
        for (int i = 0; i < outward.length; i++) {
            var cell = cells.get(i);
            int best = -1;
            double bestDot = Double.NEGATIVE_INFINITY;
            for (int d = 0; d < 6; d++) {
                int[] v = Orientation.VEC[d];
                if (positions.contains(BlockPos.asLong(cell.x + v[0], cell.y + v[1], cell.z + v[2]))) continue;
                double dot = v[0] * (cell.x - cx) + v[1] * (cell.y - cy) + v[2] * (cell.z - cz) + (v[1] == 0 ? HORIZONTAL_BONUS : 0);
                if (dot > bestDot) {
                    bestDot = dot;
                    best = d;
                }
            }
            outward[i] = best;
        }
    }

    public List<Cell> cells() {
        return cells;
    }

    public Layout exclude(boolean[] nodes) {
        for (boolean node : nodes) {
            if (node) return new Layout(this, nodes);
        }
        return whole != null ? whole : this;
    }

    public boolean isExcluded(int index) {
        return excluded != null && excluded[index];
    }

    public boolean[] excludedNodes() {
        return excludedNodes == null ? new boolean[0] : excludedNodes.clone();
    }

    @Nullable
    public Structure source() {
        return source;
    }

    public int[] nodes() {
        var nodes = new int[cells.size()];
        for (int i = 0; i < nodes.length; i++) nodes[i] = cells.get(i).node;
        return nodes;
    }

    public int[] layers() {
        var layers = new int[cells.size()];
        for (int i = 0; i < layers.length; i++) layers[i] = cells.get(i).layer;
        return layers;
    }

    public List<TraceabilityPredicate> predicates() {
        var list = new ArrayList<TraceabilityPredicate>(cells.size());
        for (int i = 0; i < cells.size(); i++) list.add(isExcluded(i) ? null : cells.get(i).predicate);
        return list;
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    public boolean isOutward(int index) {
        return outward[index] >= 0;
    }

    private boolean isHorizontal(int index) {
        return outward[index] >= 0 && Orientation.VEC[outward[index]][1] == 0;
    }

    public IntComparator placementOrder() {
        return (a, b) -> {
            int c = Boolean.compare(!isOutward(a), !isOutward(b));
            if (c != 0) return c;
            c = Boolean.compare(!isHorizontal(a), !isHorizontal(b));
            if (c != 0) return c;
            c = Boolean.compare(cells.get(a).y - minY > REACH, cells.get(b).y - minY > REACH);
            if (c != 0) return c;
            return Integer.compare(distance(cells.get(a)), distance(cells.get(b)));
        };
    }

    private static int distance(Cell cell) {
        return Math.abs(cell.x) + Math.abs(cell.y) + Math.abs(cell.z);
    }

    public BlockPos worldPos(BlockPos controller, int index, Direction front, Direction up, boolean flip) {
        int[] w = Orientation.world(front, up, flip);
        var cell = cells.get(index);
        return new BlockPos(controller.getX() + cell.x * w[0] + cell.y * w[3] + cell.z * w[6],
                controller.getY() + cell.x * w[1] + cell.y * w[4] + cell.z * w[7],
                controller.getZ() + cell.x * w[2] + cell.y * w[5] + cell.z * w[8]);
    }

    public BlockPos.MutableBlockPos worldPos(BlockPos controller, int index, Direction front, Direction up, boolean flip, BlockPos.MutableBlockPos into) {
        int[] w = Orientation.world(front, up, flip);
        var cell = cells.get(index);
        return into.set(controller.getX() + cell.x * w[0] + cell.y * w[3] + cell.z * w[6],
                controller.getY() + cell.x * w[1] + cell.y * w[4] + cell.z * w[7],
                controller.getZ() + cell.x * w[2] + cell.y * w[5] + cell.z * w[8]);
    }

    @Nullable
    public Direction outwardWorld(int index, Direction front, Direction up, boolean flip) {
        if (required[index] != null) return required[index].getRelative(front, up, flip);
        int d = outward[index];
        if (d < 0) return null;
        int[] w = Orientation.world(front, up, flip);
        int[] v = Orientation.VEC[d];
        return Direction.fromDelta(v[0] * w[0] + v[1] * w[3] + v[2] * w[6], v[0] * w[1] + v[1] * w[4] + v[2] * w[7],
                v[0] * w[2] + v[1] * w[5] + v[2] * w[8]);
    }

    @Nullable
    public Direction outwardPreview(int index) {
        if (required[index] != null) {
            var world = required[index].getRelative(Direction.NORTH, Direction.NORTH, false);
            int[] w = Orientation.world(Direction.NORTH, Direction.NORTH, false);
            int wx = world.getStepX(), wy = world.getStepY(), wz = world.getStepZ();
            return Direction.fromDelta(wx * w[0] + wy * w[1] + wz * w[2], wx * w[3] + wy * w[4] + wz * w[5], wx * w[6] + wy * w[7] + wz * w[8]);
        }
        int d = outward[index];
        if (d < 0) return null;
        int[] v = Orientation.VEC[d];
        return Direction.fromDelta(v[0], v[1], v[2]);
    }
}
