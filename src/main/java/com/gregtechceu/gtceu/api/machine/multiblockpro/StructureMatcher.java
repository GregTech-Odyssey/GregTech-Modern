package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.api.pattern.error.SinglePredicateError;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext;
import com.gregtechceu.gtceu.core.ILevel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import com.gto.datasynclib.datastream.DataComponentKey;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;

final class StructureMatcher {

    private static final ThreadLocal<StructureMatcher> LOCAL = ThreadLocal.withInitial(StructureMatcher::new);
    private static final DataComponentKey<?>[] UNDONE_BY_POSITION = { Predicates.DataKey.ACTIVE_BLOCKS, Predicates.DataKey.RENDER_MASK };

    private final ArrayList<Placement> log = new ArrayList<>();
    private final ArrayList<Mark> marks = new ArrayList<>();
    private final Reference2IntOpenHashMap<ParamKey> params = new Reference2IntOpenHashMap<>();
    private final LongOpenHashSet scratch = new LongOpenHashSet();
    private final ArrayList<MachineProtocol> machineProtocols = new ArrayList<>();
    private final ArrayList<MetaMachine> machines = new ArrayList<>();
    private final ArrayList<MachineProtocol> portProtocols = new ArrayList<>();
    private final ArrayList<BlockPos> ports = new ArrayList<>();
    private int markDepth;
    private int optionalFormed;
    private int optionalMissing;
    private boolean busy;
    private MultiblockState state;
    private boolean save;
    private boolean checkOverlap;
    private boolean aborted;
    private int[] world;
    private int cx, cy, cz;
    @Nullable
    private Long2ObjectOpenHashMap<?> savedPredicates;
    @Nullable
    private PatternError deepest;
    private int deepestDepth;
    @Nullable
    private Size measured;
    private CompiledStructure current;
    private Structure definition;

    static StructureMatcher acquire() {
        var matcher = LOCAL.get();
        return matcher.busy ? new StructureMatcher() : matcher;
    }

    boolean match(Structure structure, MultiblockState state, BlockPos center, Direction front, Direction up, boolean flip, boolean save) {
        busy = true;
        try {
            if (structure.measures.length > 0) return matchMeasured(structure, state, center, front, up, flip, save);
            return matchSingle(structure.concreteDefault(), state, center, front, up, flip, save);
        } finally {
            busy = false;
            this.state = null;
            this.current = null;
            this.definition = null;
            this.savedPredicates = null;
            this.deepest = null;
            log.clear();
            params.clear();
            machineProtocols.clear();
            machines.clear();
            portProtocols.clear();
            ports.clear();
            for (int i = 0, size = marks.size(); i < size; i++) marks.get(i).clear();
        }
    }

    private boolean matchMeasured(Structure structure, MultiblockState state, BlockPos center, Direction front, Direction up, boolean flip, boolean save) {
        state.clear();
        int[] w = Orientation.world(front, up, flip);
        var measures = structure.measures;
        var measuring = new MultiblockState(state.controller, state.world, center);
        int[] values = new int[measures.length];
        for (int i = 0; i < measures.length; i++) {
            var measure = measures[i];
            int[] v = Orientation.VEC[measure.direction()];
            int dx = v[0] * w[0] + v[1] * w[3] + v[2] * w[6];
            int dy = v[0] * w[1] + v[1] * w[4] + v[2] * w[7];
            int dz = v[0] * w[2] + v[1] * w[5] + v[2] * w[8];
            int found = -1;
            BlockPos pos = center;
            for (int step = 1; step <= measure.max(); step++) {
                pos = new BlockPos(center.getX() + dx * step, center.getY() + dy * step, center.getZ() + dz * step);
                measuring.update(pos, measure.until());
                if (measuring.getBlockState() == ILevel.OUTSIDE_WORLD_BLOCK) {
                    state.update(pos, null);
                    state.setError(MultiblockState.UNLOAD_ERROR.copy());
                    return false;
                }
                if (matchesEdge(measure.until(), measuring)) {
                    found = step;
                    break;
                }
            }
            if (found < measure.min()) {
                state.update(pos, null);
                state.setError(new PatternStringError("gtceu.multiblock.pattern.error.size"));
                return false;
            }
            values[i] = found;
        }
        var size = new Size(structure.measureKeys, values);
        if (structure.requirement != null && !structure.requirement.test(size)) {
            state.update(center, null);
            state.setError(new PatternStringError("gtceu.multiblock.pattern.error.size"));
            return false;
        }
        measured = size;
        try {
            return matchSingle(structure.forSize(size), state, center, front, up, flip, save);
        } finally {
            measured = null;
        }
    }

    private static boolean matchesEdge(TraceabilityPredicate edge, MultiblockState measuring) {
        for (var simple : edge.common) {
            if (simple.predicate.test(measuring)) return true;
        }
        for (var simple : edge.limited) {
            if (simple.predicate.test(measuring)) return true;
        }
        return false;
    }

    private boolean matchSingle(CompiledStructure structure, MultiblockState state, BlockPos center, Direction front, Direction up, boolean flip, boolean save) {
        state.clear();
        this.state = state;
        this.current = structure;
        this.definition = structure.definition;
        this.save = save;
        this.checkOverlap = definition.multiPiece;
        this.aborted = false;
        this.world = Orientation.world(front, up, flip);
        this.cx = center.getX();
        this.cy = center.getY();
        this.cz = center.getZ();
        this.deepest = null;
        this.deepestDepth = -1;
        this.markDepth = 0;
        this.optionalFormed = 0;
        this.optionalMissing = 0;
        this.savedPredicates = save ? state.getMatchContext().getPredicates() : null;
        var root = Placement.root(structure.root);
        root.toWorld(world, cx, cy, cz);
        if (!test(root, false)) return false;
        log.add(root);
        if (!bindings(root, 0)) {
            if (!aborted && deepest != null) state.setError(deepest);
            return false;
        }
        for (var it = state.getGlobalCount().reference2IntEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            if (entry.getIntValue() < entry.getKey().minCount) {
                state.setError(new SinglePredicateError(entry.getKey(), 1));
                return false;
            }
        }
        if (measured != null) {
            for (int i = 0; i < measured.size(); i++) params.put(measured.key(i), measured.value(i));
        }
        state.getMatchContext().set(Assembly.KEY, new Assembly(params, optionalFormed, optionalMissing, machineProtocols, machines, portProtocols, ports));
        state.setNeededFlip(flip);
        state.setError(null);
        return true;
    }

    private boolean bindings(Placement host, int node) {
        var children = definition.childNodes[node];
        var ports = definition.childPorts[node];
        for (int i = 0; i < children.length; i++) {
            if (!slot(host, host.piece.port(ports[i]), children[i])) return false;
        }
        return true;
    }

    private boolean slot(Placement host, Piece.Port port, int node) {
        var slot = definition.slots[node];
        if (slot instanceof Slot.PieceSlot pieceSlot) return pieceSlot(host, port, node, pieceSlot);
        if (slot instanceof Slot.MachineSlot machineSlot) return machineSlot(host, port, machineSlot);
        return choiceSlot(host, port, node, (Slot.ChoiceSlot) slot);
    }

    private boolean machineSlot(Placement host, Piece.Port port, Slot.MachineSlot slot) {
        var cells = port.cells();
        for (int i = 0; i < cells.length; i += 3) {
            var pos = host.worldPos(cells[i], cells[i + 1], cells[i + 2]);
            portProtocols.add(slot.protocol);
            ports.add(pos);
            if (ILevel.asyncGetBlockEntity(state.world, pos) instanceof MetaMachineBlockEntity holder && slot.protocol.accepts(holder.getMetaMachine().getDefinition())) {
                machineProtocols.add(slot.protocol);
                machines.add(holder.getMetaMachine());
            }
        }
        return true;
    }

    private boolean pieceSlot(Placement host, Piece.Port port, int node, Slot.PieceSlot slot) {
        int base = markDepth;
        var piece = current.pieces[node];
        var in = piece.port(slot.in);
        var reflexiveOut = slot.reflexiveOut == null ? null : piece.port(slot.reflexiveOut);
        var reflexiveIn = slot.reflexiveIn == null ? null : piece.port(slot.reflexiveIn);
        Mark[] steps = slot.backtrack && !slot.children.isEmpty() ? new Mark[slot.max] : null;
        Placement previous = host;
        Piece.Port previousPort = port;
        Piece.Port enterPort = in;
        int count = 0;
        while (count < slot.max) {
            var placement = previous.attach(previousPort, piece, enterPort);
            if (placement == null) break;
            placement.toWorld(world, cx, cy, cz);
            var mark = mark(count >= slot.min);
            if (!test(placement, checkOverlap && overlaps(placement))) {
                if (aborted) return fail(base);
                noteFailure();
                rollback(mark, placement);
                break;
            }
            log.add(placement);
            if (steps != null) steps[count] = mark;
            else release(mark);
            count++;
            if (reflexiveOut == null || reflexiveIn == null) break;
            previous = placement;
            previousPort = reflexiveOut;
            enterPort = reflexiveIn;
        }
        if (count < slot.min) return fail(base);
        if (steps == null && !slot.children.isEmpty()) {
            if (!children(node, slot, count, host, port)) return fail(base);
        } else if (steps != null) {
            while (true) {
                var mark = mark(count > slot.min);
                if (children(node, slot, count, host, port)) {
                    release(mark);
                    break;
                }
                if (aborted) return fail(base);
                noteFailure();
                rollback(mark, null);
                if (count <= slot.min) return fail(base);
                rollback(steps[--count], null);
            }
        }
        markDepth = base;
        if (slot.min == 0 && slot.max == 1) {
            if (count > 0) optionalFormed++;
            else optionalMissing++;
        }
        if (slot.param != null) params.put(slot.param, count);
        return true;
    }

    private boolean children(int node, Slot.PieceSlot slot, int count, Placement host, Piece.Port port) {
        if (count > 0) return bindings(log.getLast(), node);
        var children = definition.childNodes[node];
        var ports = definition.childPorts[node];
        for (int i = 0; i < children.length; i++) {
            if (ports[i] == slot.reflexiveOut && !slot(host, port, children[i])) return false;
        }
        return true;
    }

    private boolean choiceSlot(Placement host, Piece.Port port, int node, Slot.ChoiceSlot slot) {
        var options = definition.childNodes[node];
        for (int i = 0; i < options.length; i++) {
            var mark = mark(slot.optional || i + 1 < options.length);
            if (slot(host, port, options[i])) {
                release(mark);
                if (slot.optional) optionalFormed++;
                if (slot.param != null) params.put(slot.param, i + 1);
                return true;
            }
            if (aborted) return false;
            noteFailure();
            rollback(mark, null);
        }
        if (!slot.optional) return false;
        optionalMissing++;
        if (slot.param != null) params.put(slot.param, 0);
        return true;
    }

    private boolean fail(int base) {
        markDepth = base;
        return false;
    }

    private boolean overlaps(Placement placement) {
        for (int i = 0, size = log.size(); i < size; i++) {
            if (placement.intersects(log.get(i))) return true;
        }
        return false;
    }

    private boolean test(Placement placement, boolean overlap) {
        var piece = placement.piece;
        var layerCount = state.getLayerCount();
        var masks = current.limitMasks == null ? null : current.limitMasks.get(piece);
        int i = 0;
        for (int aisle = 0; aisle < piece.aisleCount; aisle++) {
            layerCount.clear();
            for (int end = piece.aisleEnd[aisle]; i < end; i++) {
                var pos = new BlockPos(placement.worldX(i), placement.worldY(i), placement.worldZ(i));
                if (overlap && claimed(pos.asLong())) {
                    placement.tested = i;
                    state.update(pos, piece.predicate(i));
                    state.setError(new PatternError());
                    return false;
                }
                int result = BlockPattern.testCell(state, pos, piece.predicate(i), save);
                if (result != BlockPattern.CELL_PASS) {
                    placement.tested = i + 1;
                    if (result == BlockPattern.CELL_ABORT) {
                        aborted = true;
                    } else if (!state.hasError()) {
                        state.setError(new PatternError());
                    }
                    return false;
                }
                if (masks != null && masks[i] != 0 && !countLimits(masks[i])) {
                    placement.tested = i + 1;
                    return false;
                }
            }
            if (!layerCount.isEmpty()) {
                for (var it = layerCount.reference2IntEntrySet().fastIterator(); it.hasNext();) {
                    var entry = it.next();
                    if (entry.getIntValue() < entry.getKey().minLayerCount) {
                        placement.tested = i;
                        state.setError(new SinglePredicateError(entry.getKey(), 3));
                        return false;
                    }
                }
            }
        }
        placement.tested = i;
        return true;
    }

    private boolean countLimits(int mask) {
        var global = state.getGlobalCount();
        var limitPredicates = current.limitPredicates;
        var limitMax = current.limitMax;
        for (int i = 0; i < limitPredicates.length; i++) {
            if ((mask & 1 << i) == 0) continue;
            for (var simple : limitPredicates[i]) {
                if (!simple.predicate.test(state)) continue;
                if (global.mergeInt(simple, 1, Integer::sum) > limitMax[i]) {
                    state.setError(new LimitError(simple, limitMax[i]));
                    return false;
                }
            }
        }
        return true;
    }

    private boolean claimed(long pos) {
        return save ? savedPredicates.containsKey(pos) : state.cache.contains(pos);
    }

    private void noteFailure() {
        if (log.size() >= deepestDepth) {
            deepestDepth = log.size();
            deepest = state.error;
        }
    }

    private Mark mark(boolean values) {
        Mark mark;
        if (markDepth < marks.size()) {
            mark = marks.get(markDepth);
        } else {
            mark = new Mark(markDepth);
            marks.add(mark);
        }
        markDepth++;
        mark.logSize = log.size();
        var global = state.getGlobalCount();
        int size = global.size();
        if (mark.globalKeys.length < size) {
            mark.globalKeys = new SimplePredicate[size + 8];
            mark.globalValues = new int[size + 8];
        }
        int i = 0;
        for (var it = global.reference2IntEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            mark.globalKeys[i] = entry.getKey();
            mark.globalValues[i++] = entry.getIntValue();
        }
        mark.globalCount = size;
        mark.params.clear();
        if (!params.isEmpty()) mark.params.putAll(params);
        state.getMatchContext().save(mark.context, values, UNDONE_BY_POSITION);
        mark.optionalFormed = optionalFormed;
        mark.optionalMissing = optionalMissing;
        mark.machineCount = machines.size();
        mark.portCount = ports.size();
        return mark;
    }

    private void release(Mark mark) {
        markDepth = mark.index;
    }

    private void rollback(Mark mark, @Nullable Placement partial) {
        if (partial != null) undo(partial);
        for (int i = log.size() - 1; i >= mark.logSize; i--) {
            undo(log.remove(i));
        }
        var global = state.getGlobalCount();
        global.clear();
        for (int i = 0; i < mark.globalCount; i++) {
            global.put(mark.globalKeys[i], mark.globalValues[i]);
        }
        params.clear();
        params.putAll(mark.params);
        state.getMatchContext().restore(mark.context);
        optionalFormed = mark.optionalFormed;
        optionalMissing = mark.optionalMissing;
        for (int i = machines.size() - 1; i >= mark.machineCount; i--) {
            machines.remove(i);
            machineProtocols.remove(i);
        }
        for (int i = ports.size() - 1; i >= mark.portCount; i--) {
            ports.remove(i);
            portProtocols.remove(i);
        }
        release(mark);
    }

    private void undo(Placement placement) {
        int tested = placement.tested;
        if (tested == 0) return;
        var context = state.getMatchContext();
        LongSet active = context.containsKey(Predicates.DataKey.ACTIVE_BLOCKS) ? context.get(Predicates.DataKey.ACTIVE_BLOCKS) : null;
        LongSet mask = context.containsKey(Predicates.DataKey.RENDER_MASK) ? context.get(Predicates.DataKey.RENDER_MASK) : null;
        var parts = context.getParts();
        boolean hasParts = !parts.isEmpty();
        if (hasParts) scratch.clear();
        for (int i = 0; i < tested; i++) {
            long pos = BlockPos.asLong(placement.worldX(i), placement.worldY(i), placement.worldZ(i));
            if (save) savedPredicates.remove(pos);
            else state.cache.remove(pos);
            state.sharedCache.remove(pos);
            state.blockEntityCache.remove(pos);
            if (active != null) active.remove(pos);
            if (mask != null) mask.remove(pos);
            if (hasParts) scratch.add(pos);
        }
        if (hasParts) parts.removeIf(part -> scratch.contains(part.self().getPos().asLong()));
    }

    private static final class Mark {

        final int index;
        final Reference2IntOpenHashMap<ParamKey> params = new Reference2IntOpenHashMap<>();
        final PatternMatchContext.Checkpoint context = new PatternMatchContext.Checkpoint();
        SimplePredicate[] globalKeys = new SimplePredicate[8];
        int[] globalValues = new int[8];
        int globalCount;
        int optionalFormed;
        int optionalMissing;
        int machineCount;
        int portCount;
        int logSize;

        Mark(int index) {
            this.index = index;
        }

        void clear() {
            context.clear();
            params.clear();
            Arrays.fill(globalKeys, null);
            globalCount = 0;
        }
    }
}
