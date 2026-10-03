package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * 已成型多方块里可放部件（仓室、总线等）的格子：主线程取成型参数，后台展开结构并筛出候选含部件的格子，回主线程与成型格取交集。
 */
public final class PartCells {

    public static final int MAX_CELLS = 8192;

    private PartCells() {}

    public static boolean acceptsPart(TraceabilityPredicate predicate, MachineDefinition owner) {
        boolean[] part = { false };
        predicate.forEachSimple(simple -> {
            if (simple == null || part[0] || simple.candidates == null) return;
            for (var block : simple.candidates.get()) {
                if (block instanceof MetaMachineBlock machine && machine.definition != owner && !(machine.definition instanceof MultiblockMachineDefinition)) {
                    part[0] = true;
                    return;
                }
            }
        });
        return part[0];
    }

    @Nullable
    public static Request request(IMultiController controller) {
        if (!controller.isFormed()) return null;
        var machine = controller.self();
        if (!(machine.getDefinition() instanceof MultiblockMachineDefinition definition)) return null;
        var structure = definition.getStructure();
        var assembly = controller.getAssembly();
        if (structure == null || assembly == null) return null;
        return new Request(definition, structure, structure.valuesOf(assembly), machine.getPos().immutable(), machine.getFrontFacing(),
                machine.getUpwardsFacing(), machine.isFlipped());
    }

    public static long[] candidates(Request request) {
        var layout = request.structure.layout(request.values);
        if (layout == null) return new long[0];
        var accepts = new Reference2BooleanOpenHashMap<TraceabilityPredicate>();
        var cells = layout.cells();
        var cursor = new BlockPos.MutableBlockPos();
        var positions = new LongArrayList();
        for (int i = 0, size = cells.size(); i < size; i++) {
            var predicate = cells.get(i).predicate();
            boolean part;
            if (accepts.containsKey(predicate)) {
                part = accepts.getBoolean(predicate);
            } else {
                part = acceptsPart(predicate, request.definition);
                accepts.put(predicate, part);
            }
            if (part) positions.add(layout.worldPos(request.origin, i, request.front, request.up, request.flip, cursor).asLong());
        }
        return positions.toLongArray();
    }

    @Nullable
    public static Result finish(IMultiController controller, Request request, long[] candidates, BlockPos viewer) {
        var machine = controller.self();
        if (machine.isRemoved() || !controller.isFormed() || machine.getDefinition() != request.definition || !machine.getPos().equals(request.origin) ||
                machine.getFrontFacing() != request.front || machine.getUpwardsFacing() != request.up || machine.isFlipped() != request.flip) {
            return null;
        }
        var state = controller.getMultiblockState();
        var parts = controller.getParts();
        var installed = new LongOpenHashSet(parts.length);
        for (var part : parts) installed.add(part.self().getPos().asLong());
        var open = new LongArrayList();
        var filled = new LongArrayList();
        long self = request.origin.asLong();
        for (long pos : candidates) {
            if (pos == self || !state.inStructure(pos)) continue;
            if (installed.contains(pos)) filled.add(pos);
            else open.add(pos);
        }
        int total = open.size() + filled.size();
        if (total <= MAX_CELLS) return new Result(request.origin, open.toLongArray(), filled.toLongArray(), total);
        long[] all = new long[total];
        open.getElements(0, all, 0, open.size());
        filled.getElements(0, all, open.size(), filled.size());
        long center = viewer.asLong();
        LongArrays.quickSort(all, (a, b) -> Long.compare(distance(a, center), distance(b, center)));
        var keptOpen = new LongArrayList(MAX_CELLS);
        var keptFilled = new LongArrayList();
        for (int i = 0; i < MAX_CELLS; i++) {
            long pos = all[i];
            if (installed.contains(pos)) keptFilled.add(pos);
            else keptOpen.add(pos);
        }
        return new Result(request.origin, keptOpen.toLongArray(), keptFilled.toLongArray(), total);
    }

    private static long distance(long pos, long center) {
        long dx = BlockPos.getX(pos) - BlockPos.getX(center);
        long dy = BlockPos.getY(pos) - BlockPos.getY(center);
        long dz = BlockPos.getZ(pos) - BlockPos.getZ(center);
        return dx * dx + dy * dy + dz * dz;
    }

    public record Request(MultiblockMachineDefinition definition, Structure structure, int[] values, BlockPos origin, Direction front, Direction up,
                          boolean flip) {}

    public record Result(BlockPos controller, long[] open, long[] filled, int total) {

        public int shown() {
            return open.length + filled.length;
        }

        public boolean isTruncated() {
            return shown() < total;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeLong(controller.asLong());
            writeCells(buf, open);
            writeCells(buf, filled);
        }

        public static Result read(FriendlyByteBuf buf) {
            var controller = BlockPos.of(buf.readLong());
            var open = readCells(buf, controller);
            var filled = readCells(buf, controller);
            return new Result(controller, open, filled, open.length + filled.length);
        }

        private void writeCells(FriendlyByteBuf buf, long[] cells) {
            buf.writeVarInt(cells.length);
            for (long pos : cells) {
                buf.writeVarInt(BlockPos.getX(pos) - controller.getX());
                buf.writeVarInt(BlockPos.getY(pos) - controller.getY());
                buf.writeVarInt(BlockPos.getZ(pos) - controller.getZ());
            }
        }

        private static long[] readCells(FriendlyByteBuf buf, BlockPos controller) {
            int size = buf.readVarInt();
            if (size < 0 || size > MAX_CELLS || size > buf.readableBytes() / 3) throw new IllegalArgumentException("part cells: " + size);
            long[] cells = new long[size];
            for (int i = 0; i < size; i++) {
                cells[i] = BlockPos.asLong(controller.getX() + buf.readVarInt(), controller.getY() + buf.readVarInt(), controller.getZ() + buf.readVarInt());
            }
            return cells;
        }
    }
}
