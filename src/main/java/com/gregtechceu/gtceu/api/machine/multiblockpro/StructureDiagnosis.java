package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.core.Iblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import com.gto.datasynclib.datastream.codec.StreamCodec;
import com.gto.datasynclib.util.ByteBufCodecExtends;
import com.suried.gtoutils.level.LoadedChunks;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * 全量结构诊断：按"吻合的方块最多"推断朝向、尺寸与可选段，再按这份蓝图逐格检查，把问题按类型、需要的方块与实际方块分组。
 */
public final class StructureDiagnosis {

    public static final int NORMAL = 0;
    public static final int WARNING = 1;
    public static final int ERROR = 2;
    public static final int MAX_ROWS = 64;
    public static final int MAX_ROW_POSITIONS = 256;
    public static final int MAX_POSITIONS = 2048;
    private static final int MIN_ROW_POSITIONS = 16;
    private static final int MAX_DETAIL_POSITIONS = 4;
    private static final int MAX_DETAIL_KINDS = 6;
    public static final int PROGRESS_SCALE = 1000;
    private static final int PROGRESS_STRIDE = 1023;
    private static final int MAX_LINES = 6;
    private static final int CELL_BUDGET = 2_000_000;
    private static final int MAX_CHAIN = 256;

    public static final Component MISSING = Component.translatable("gtceu.structure.diagnosis.missing");
    public static final Component WRONG = Component.translatable("gtceu.structure.diagnosis.wrong");
    public static final Component EXPECTED = Component.translatable("gtceu.structure.diagnosis.expected");
    public static final Component FOUND = Component.translatable("gtceu.structure.diagnosis.found");
    public static final Component POSITION = Component.translatable("gtceu.structure.diagnosis.position");
    public static final Component SUBJECT = Component.translatable("gtceu.structure.diagnosis.subject");
    public static final Component POSITION_FREE = Component.translatable("gtceu.structure.diagnosis.position.free");
    public static final Component TOO_FEW = Component.translatable("gtceu.structure.diagnosis.too_few");
    public static final Component TOO_MANY = Component.translatable("gtceu.structure.diagnosis.too_many");
    public static final Component TOO_FEW_LAYER = Component.translatable("gtceu.structure.diagnosis.too_few_layer");
    public static final Component TOO_MANY_LAYER = Component.translatable("gtceu.structure.diagnosis.too_many_layer");
    public static final Component SIZE = Component.translatable("gtceu.structure.diagnosis.size");
    public static final Component FIXED = Component.translatable("gtceu.structure.diagnosis.basis.fixed");
    public static final Component MIRRORED = Component.translatable("gtceu.structure.diagnosis.basis.mirrored");
    public static final Component NONE = Component.translatable("gtceu.structure.diagnosis.basis.none");
    public static final Component SEPARATOR = Component.translatable("gtceu.structure.diagnosis.basis.separator");
    public static final Component UNMEASURED = Component.translatable("gtceu.structure.diagnosis.unmeasured");
    public static final Component TOO_COMPLEX = Component.translatable("gtceu.structure.diagnosis.too_complex");
    public static final Component FAILED = Component.translatable("gtceu.structure.diagnosis.failed");

    private static final StructureIssue.Type[] ORDER = { StructureIssue.SIZE, StructureIssue.UNLOADED, StructureIssue.SHARED, StructureIssue.MISMATCH,
            StructureIssue.MIXED, StructureIssue.TOO_MANY, StructureIssue.TOO_MANY_LAYER, StructureIssue.TOO_FEW, StructureIssue.TOO_FEW_LAYER,
            StructureIssue.FORBIDDEN, StructureIssue.CONDITION };

    private StructureDiagnosis() {}

    public record Request(IMultiController controller, ServerLevel level, Structure structure, BlockPos center, Direction[] facings, Direction up,
                          boolean allowFlip) {}

    public record Line(Component label, Component value, Component detail) {

        private static final StreamCodec<FriendlyByteBuf, Line> CODEC = new StreamCodec<>() {

            @Override
            public void encode(FriendlyByteBuf buf, Line line) {
                ByteBufCodecExtends.COMPONENT_CODEC.encode(buf, line.label);
                ByteBufCodecExtends.COMPONENT_CODEC.encode(buf, line.value);
                ByteBufCodecExtends.COMPONENT_CODEC.encode(buf, line.detail);
            }

            @Override
            public Line decode(FriendlyByteBuf buf) {
                return new Line(ByteBufCodecExtends.COMPONENT_CODEC.decode(buf), ByteBufCodecExtends.COMPONENT_CODEC.decode(buf),
                        ByteBufCodecExtends.COMPONENT_CODEC.decode(buf));
            }
        };
    }

    public record Row(int severity, int count, Component title, Component value, @Nullable Line subject, Item icon, List<Line> lines, long[] positions) {

        public static final StreamCodec<FriendlyByteBuf, Row> CODEC = new StreamCodec<>() {

            @Override
            public void encode(FriendlyByteBuf buf, Row row) {
                buf.writeByte(row.severity);
                buf.writeVarInt(row.count);
                ByteBufCodecExtends.COMPONENT_CODEC.encode(buf, row.title);
                ByteBufCodecExtends.COMPONENT_CODEC.encode(buf, row.value);
                buf.writeBoolean(row.subject != null);
                if (row.subject != null) Line.CODEC.encode(buf, row.subject);
                buf.writeId(BuiltInRegistries.ITEM, row.icon);
                buf.writeVarInt(row.lines.size());
                for (var line : row.lines) Line.CODEC.encode(buf, line);
                buf.writeVarInt(row.positions.length);
                for (long pos : row.positions) buf.writeLong(pos);
            }

            @Override
            public Row decode(FriendlyByteBuf buf) {
                int severity = buf.readByte();
                int count = buf.readVarInt();
                var title = ByteBufCodecExtends.COMPONENT_CODEC.decode(buf);
                var value = ByteBufCodecExtends.COMPONENT_CODEC.decode(buf);
                var subject = buf.readBoolean() ? Line.CODEC.decode(buf) : null;
                var icon = buf.readById(BuiltInRegistries.ITEM);
                int lineCount = Math.min(buf.readVarInt(), MAX_LINES);
                var lines = new ArrayList<Line>(lineCount);
                for (int i = 0; i < lineCount; i++) lines.add(Line.CODEC.decode(buf));
                int positionCount = Math.min(buf.readVarInt(), MAX_ROW_POSITIONS);
                var positions = new long[positionCount];
                for (int i = 0; i < positionCount; i++) positions[i] = buf.readLong();
                return new Row(severity, count, title, value, subject, icon == null ? Items.AIR : icon, List.copyOf(lines), positions);
            }
        };

        @Override
        public boolean equals(Object o) {
            return o instanceof Row other && severity == other.severity && count == other.count && title.equals(other.title) && value.equals(other.value) &&
                    Objects.equals(subject, other.subject) && icon == other.icon && lines.equals(other.lines) && Arrays.equals(positions, other.positions);
        }

        @Override
        public int hashCode() {
            return Objects.hash(severity, count, title, value, subject, icon, lines) * 31 + Arrays.hashCode(positions);
        }
    }

    public record Report(List<Row> rows, int problems, int groups, Component basis, @Nullable Component note) {}

    @Nullable
    public static Request request(IMultiController controller) {
        var machine = controller.self();
        if (machine.isRemoved() || !(machine.getLevel() instanceof ServerLevel level)) return null;
        if (!(machine.getDefinition() instanceof MultiblockMachineDefinition definition) || !definition.hasStructure()) return null;
        var structure = definition.getStructure();
        if (structure == null) return null;
        var up = machine.getUpwardsFacing();
        var facings = controller.hasFrontFacing() ? new Direction[] { machine.getFrontFacing() } : structure.freeFacings(up);
        return new Request(controller, level, structure, machine.getPos().immutable(), facings, up, machine.allowFlip());
    }

    public static Report run(Request request) {
        return run(request, null);
    }

    public static Report run(Request request, @Nullable IntConsumer progress) {
        try {
            return new Run(request, progress).execute();
        } catch (TooComplex e) {
            return new Report(List.of(), 0, 0, NONE, TOO_COMPLEX);
        } catch (RuntimeException e) {
            GTCEu.LOGGER.error("Structure diagnosis of {} failed", request.center, e);
            return new Report(List.of(), 0, 0, NONE, FAILED);
        }
    }

    public static Component describeFormed(IMultiController controller) {
        var machine = controller.self();
        if (!(machine.getDefinition() instanceof MultiblockMachineDefinition definition) || !definition.hasStructure()) return FIXED;
        var structure = definition.getStructure();
        if (structure == null) return FIXED;
        return describeSize(structure, structure.valuesOf(controller.getAssembly()), machine.allowFlip() && machine.isFlipped());
    }

    static Component describeSize(Structure structure, int[] values, boolean flip) {
        var parts = new ArrayList<Component>();
        var options = structure.options();
        for (int i = 0; i < options.size() && i < values.length; i++) {
            var option = options.get(i);
            if (option.key() == null || option.min() == option.max()) continue;
            int value = values[i];
            Component shown = option.kind() == Structure.Option.Kind.CHOICE ?
                    (value == 0 ? NONE : Component.translatable("gtceu.structure.diagnosis.basis.choice", value)) : Component.literal(Integer.toString(value));
            parts.add(Component.translatable("gtceu.structure.diagnosis.basis.entry", option.key().getName(), shown));
        }
        if (flip) parts.add(MIRRORED);
        if (parts.isEmpty()) return FIXED;
        var result = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) result.append(SEPARATOR);
            result.append(parts.get(i));
        }
        return result;
    }

    private static final class TooComplex extends RuntimeException {

        private static final TooComplex INSTANCE = new TooComplex();

        private TooComplex() {
            super(null, null, false, false);
        }
    }

    private record Interpretation(Direction front, boolean flip, int[] values, int score, CompiledStructure compiled) {}

    private static final class Run {

        private final Request request;
        private final Structure structure;
        private final MultiblockState probe;
        private final Reference2BooleanOpenHashMap<TraceabilityPredicate> weighted = new Reference2BooleanOpenHashMap<>();
        private final int cx, cy, cz;
        private CompiledStructure current;
        private int[] world;
        private int budget;
        @Nullable
        private StructureIssue measureFailure;
        @Nullable
        private final IntConsumer progress;
        private final int steps;

        private Run(Request request, @Nullable IntConsumer progress) {
            this.request = request;
            this.progress = progress;
            this.steps = request.facings.length * (request.allowFlip ? 2 : 1) + 1;
            this.structure = request.structure;
            this.probe = new MultiblockState(request.controller, request.level, request.center);
            this.cx = request.center.getX();
            this.cy = request.center.getY();
            this.cz = request.center.getZ();
        }

        private Report execute() {
            if (request.controller.isFormed()) return new Report(List.of(), 0, 0, NONE, null);
            Interpretation best = null;
            int step = 0;
            for (var front : request.facings) {
                for (int f = 0; f < (request.allowFlip ? 2 : 1); f++) {
                    report(step++, 0, 1);
                    boolean flip = f == 1;
                    var size = measure(front, flip);
                    if (size == null) continue;
                    var compiled = structure.measures.length == 0 ? structure.concreteDefault() : structure.forSize(size);
                    var values = structure.defaultValues();
                    for (int i = 0; i < size.size(); i++) values[i] = size.value(i);
                    int score = evaluate(compiled, front, flip, values);
                    if (best == null || score > best.score) best = new Interpretation(front, flip, values, score, compiled);
                }
            }
            report(step, 0, 1);
            if (best == null) {
                var findings = new Findings();
                if (measureFailure != null) findings.add(measureFailure);
                var rows = findings.rows(null);
                return new Report(rows, findings.problems(), findings.groups(), NONE, UNMEASURED);
            }
            return scan(best);
        }

        @Nullable
        private Size measure(Direction front, boolean flip) {
            var measures = structure.measures;
            if (measures.length == 0) return new Size(new ParamKey[0], new int[0]);
            int[] w = Orientation.world(front, request.up, flip);
            int[] values = new int[measures.length];
            for (int i = 0; i < measures.length; i++) {
                var measure = measures[i];
                int[] v = Orientation.VEC[measure.direction()];
                int dx = v[0] * w[0] + v[1] * w[3] + v[2] * w[6];
                int dy = v[0] * w[1] + v[1] * w[4] + v[2] * w[7];
                int dz = v[0] * w[2] + v[1] * w[5] + v[2] * w[8];
                int found = -1;
                BlockPos pos = request.center;
                for (int step = 1; step <= measure.max(); step++) {
                    pos = new BlockPos(cx + dx * step, cy + dy * step, cz + dz * step);
                    probe.update(pos, measure.until());
                    if (probe.getBlockState() == LoadedChunks.UNLOADED) {
                        probe.setIssue(StructureIssue.unloaded());
                        noteMeasureFailure(probe.getIssue());
                        return null;
                    }
                    if (matchesAny(measure.until())) {
                        found = step;
                        break;
                    }
                }
                if (found < measure.min()) {
                    noteMeasureFailure(StructureIssue.size(measure.key(), found, measure.min(), measure.max()));
                    return null;
                }
                values[i] = found;
            }
            var size = new Size(structure.measureKeys, values);
            if (structure.requirement != null && !structure.requirement.test(size)) {
                noteMeasureFailure(StructureIssue.sizeCombination());
                return null;
            }
            return size;
        }

        private void noteMeasureFailure(StructureIssue issue) {
            if (measureFailure == null) measureFailure = issue;
        }

        private boolean matchesAny(TraceabilityPredicate predicate) {
            for (var simple : predicate.common) {
                if (simple.predicate.test(probe)) return true;
            }
            for (var simple : predicate.limited) {
                if (simple.predicate.test(probe)) return true;
            }
            return false;
        }

        private int evaluate(CompiledStructure compiled, Direction front, boolean flip, int[] values) {
            this.current = compiled;
            this.world = Orientation.world(front, request.up, flip);
            var root = Placement.root(compiled.root);
            root.toWorld(world, cx, cy, cz);
            return score(root) + bindings(root, 0, values);
        }

        private int bindings(Placement host, int node, int[] values) {
            var children = structure.childNodes[node];
            var ports = structure.childPorts[node];
            int total = 0;
            for (int i = 0; i < children.length; i++) total += slot(host, host.piece.port(ports[i]), children[i], values);
            return total;
        }

        private int slot(Placement host, Piece.Port port, int node, int[] values) {
            var slot = structure.slots[node];
            if (slot instanceof Slot.PieceSlot pieceSlot) return pieceSlot(host, port, node, pieceSlot, values);
            if (slot instanceof Slot.ChoiceSlot choiceSlot) return choiceSlot(host, port, node, choiceSlot, values);
            return 0;
        }

        private int pieceSlot(Placement host, Piece.Port port, int node, Slot.PieceSlot slot, int[] values) {
            var piece = current.pieces[node];
            var in = piece.port(slot.in);
            var reflexiveOut = slot.reflexiveOut == null ? null : piece.port(slot.reflexiveOut);
            var reflexiveIn = slot.reflexiveIn == null ? null : piece.port(slot.reflexiveIn);
            int max = Math.min(slot.max, MAX_CHAIN);
            var placements = new ArrayList<Placement>();
            var prefix = new IntArrayList();
            prefix.add(0);
            Placement previous = host;
            Piece.Port previousPort = port;
            Piece.Port enterPort = in;
            while (placements.size() < max) {
                var placement = previous.attach(previousPort, piece, enterPort);
                if (placement == null) break;
                placement.toWorld(world, cx, cy, cz);
                placements.add(placement);
                prefix.add(prefix.getInt(prefix.size() - 1) + score(placement));
                if (reflexiveOut == null || reflexiveIn == null) break;
                previous = placement;
                previousPort = reflexiveOut;
                enterPort = reflexiveIn;
            }
            boolean hasChildren = !slot.children.isEmpty();
            int low = Math.min(Math.max(slot.min, 0), placements.size());
            int bestScore = Integer.MIN_VALUE;
            int bestCount = low;
            int[] bestValues = values;
            for (int count = low; count <= placements.size(); count++) {
                int[] trial = hasChildren ? values.clone() : values;
                int total = prefix.getInt(count) + (hasChildren ? children(node, slot, count, placements, host, port, trial) : 0);
                if (total > bestScore) {
                    bestScore = total;
                    bestCount = count;
                    bestValues = trial;
                }
            }
            if (bestValues != values) System.arraycopy(bestValues, 0, values, 0, values.length);
            int option = structure.slotOptions[node];
            if (option >= 0) values[option] = Math.max(slot.min, Math.min(slot.max, bestCount));
            return bestScore;
        }

        private int children(int node, Slot.PieceSlot slot, int count, List<Placement> placements, Placement host, Piece.Port port, int[] values) {
            if (count > 0) return bindings(placements.get(count - 1), node, values);
            var children = structure.childNodes[node];
            var ports = structure.childPorts[node];
            int total = 0;
            for (int i = 0; i < children.length; i++) {
                if (ports[i] == slot.reflexiveOut) total += slot(host, port, children[i], values);
            }
            return total;
        }

        private int choiceSlot(Placement host, Piece.Port port, int node, Slot.ChoiceSlot slot, int[] values) {
            var options = structure.childNodes[node];
            int bestScore = slot.optional ? 0 : Integer.MIN_VALUE;
            int bestChoice = slot.optional ? 0 : 1;
            int[] bestValues = values.clone();
            for (int i = 0; i < options.length; i++) {
                var trial = values.clone();
                int score = slot(host, port, options[i], trial);
                if (score > bestScore) {
                    bestScore = score;
                    bestChoice = i + 1;
                    bestValues = trial;
                }
            }
            System.arraycopy(bestValues, 0, values, 0, values.length);
            int option = structure.slotOptions[node];
            if (option >= 0) values[option] = bestChoice;
            return bestScore == Integer.MIN_VALUE ? 0 : bestScore;
        }

        private int score(Placement placement) {
            var piece = placement.piece;
            int total = 0;
            for (int i = 0, size = piece.size(); i < size; i++) {
                var predicate = piece.predicate(i);
                if (!isWeighted(predicate)) continue;
                spend();
                probe.update(new BlockPos(placement.worldX(i), placement.worldY(i), placement.worldZ(i)), predicate);
                total += fits(predicate) ? 1 : -1;
            }
            return total;
        }

        private boolean isWeighted(TraceabilityPredicate predicate) {
            if (weighted.containsKey(predicate)) return weighted.getBoolean(predicate);
            boolean result = !predicate.isAny() && !predicate.hasAir();
            weighted.put(predicate, result);
            return result;
        }

        private boolean fits(TraceabilityPredicate predicate) {
            if (probe.getBlockState() == LoadedChunks.UNLOADED) return false;
            probe.getMatchContext().reset();
            return matchesAny(predicate);
        }

        private void spend() {
            if (++budget > CELL_BUDGET) throw TooComplex.INSTANCE;
        }

        private void report(int step, int done, int total) {
            if (progress == null) return;
            long span = Math.max(1, total);
            progress.accept((int) (((long) step * span + done) * PROGRESS_SCALE / (steps * span)));
        }

        private Report scan(Interpretation best) {
            var layout = structure.layout(best.values);
            if (layout == null) return new Report(List.of(), 0, 0, basis(best), FAILED);
            var state = new MultiblockState(request.controller, request.level, request.center);
            var compiled = best.compiled;
            var masks = new Reference2IntOpenHashMap<TraceabilityPredicate>();
            masks.defaultReturnValue(-1);
            var findings = new Findings();
            var unfilled = new Reference2IntOpenHashMap<SimplePredicate>();
            var cells = layout.cells();
            var cursor = new BlockPos.MutableBlockPos();
            int layer = Integer.MIN_VALUE;
            for (int i = 0, size = cells.size(); i < size; i++) {
                if ((i & PROGRESS_STRIDE) == 0) report(steps - 1, i, size);
                var cell = cells.get(i);
                if (cell.layer() != layer) {
                    closeLayer(state, findings);
                    layer = cell.layer();
                }
                spend();
                var predicate = cell.predicate();
                state.update(layout.worldPos(request.center, i, best.front, request.up, best.flip, cursor).immutable(), predicate);
                if (state.getBlockState() == LoadedChunks.UNLOADED) {
                    state.setIssue(StructureIssue.unloaded());
                    findings.add(state.getIssue());
                    continue;
                }
                if (!predicate.test(state)) {
                    if (!state.hasIssue()) state.setIssue(StructureIssue.mismatch());
                    if (state.getIssue().getType() == StructureIssue.MISMATCH) countUnfilled(predicate, unfilled);
                    findings.add(state.getIssue());
                    continue;
                }
                if (predicate.testOnly()) continue;
                var block = state.getBlockState().getBlock();
                if (state.data != null && !((Iblock) block).gtceu$canMultiShared() && state.data.hasShared(state.getPos().asLong())) {
                    state.setIssue(StructureIssue.shared());
                    findings.add(state.getIssue());
                    continue;
                }
                int mask = limitMask(compiled, predicate, masks);
                if (mask != 0) countLimits(state, compiled, mask, findings);
            }
            closeLayer(state, findings);
            var global = state.getGlobalCount();
            for (var it = global.reference2IntEntrySet().fastIterator(); it.hasNext();) {
                var entry = it.next();
                var simple = entry.getKey();
                if (simple.minCount != -1 && entry.getIntValue() < simple.minCount && unfilled.getInt(simple) < simple.minCount - entry.getIntValue()) {
                    findings.add(StructureIssue.tooFew(simple, simple.minCount, entry.getIntValue()));
                }
            }
            var rows = findings.rows(global);
            return new Report(rows, findings.problems(), findings.groups(), basis(best), null);
        }

        private static void countUnfilled(TraceabilityPredicate predicate, Reference2IntOpenHashMap<SimplePredicate> unfilled) {
            for (var simple : predicate.common) {
                if (simple.minCount != -1) unfilled.addTo(simple, 1);
            }
            for (var simple : predicate.limited) {
                if (simple.minCount != -1) unfilled.addTo(simple, 1);
            }
        }

        private static void closeLayer(MultiblockState state, Findings findings) {
            var layerCount = state.getLayerCount();
            if (layerCount.isEmpty()) return;
            for (var it = layerCount.reference2IntEntrySet().fastIterator(); it.hasNext();) {
                var entry = it.next();
                var simple = entry.getKey();
                if (simple.minLayerCount != -1 && entry.getIntValue() < simple.minLayerCount) {
                    findings.add(StructureIssue.tooFewInLayer(simple, simple.minLayerCount, entry.getIntValue()));
                }
            }
            layerCount.clear();
        }

        private static int limitMask(CompiledStructure compiled, TraceabilityPredicate predicate, Reference2IntOpenHashMap<TraceabilityPredicate> masks) {
            if (compiled.limitPredicates.length == 0) return 0;
            int mask = masks.getInt(predicate);
            if (mask >= 0) return mask;
            mask = 0;
            for (int i = 0; i < compiled.limitPredicates.length; i++) {
                for (var simple : compiled.limitPredicates[i]) {
                    if (predicate.common.contains(simple) || predicate.limited.contains(simple)) mask |= 1 << i;
                }
            }
            masks.put(predicate, mask);
            return mask;
        }

        private static void countLimits(MultiblockState state, CompiledStructure compiled, int mask, Findings findings) {
            var global = state.getGlobalCount();
            for (int i = 0; i < compiled.limitPredicates.length; i++) {
                if ((mask & 1 << i) == 0) continue;
                for (var simple : compiled.limitPredicates[i]) {
                    if (!simple.predicate.test(state)) continue;
                    if (global.mergeInt(simple, 1, Integer::sum) > compiled.limitMax[i]) {
                        state.setIssue(StructureIssue.tooMany(simple, compiled.limitMax[i]));
                        findings.add(state.getIssue());
                    }
                }
            }
        }

        private Component basis(Interpretation best) {
            return describeSize(structure, best.values, best.flip);
        }
    }

    private record GroupKey(StructureIssue.Type type, @Nullable Object first, @Nullable Object second) {}

    private static final class Group {

        final StructureIssue sample;
        final LongArrayList positions = new LongArrayList();
        @Nullable
        Reference2IntOpenHashMap<Block> found;
        int count;
        private int seen;
        private int stride = 1;

        Group(StructureIssue sample) {
            this.sample = sample;
        }

        void locate(long pos) {
            if (seen++ % stride != 0) return;
            if (positions.size() == MAX_ROW_POSITIONS) {
                int kept = 0;
                for (int i = 0; i < positions.size(); i += 2) positions.set(kept++, positions.getLong(i));
                positions.size(kept);
                stride *= 2;
                if ((seen - 1) % stride != 0) return;
            }
            positions.add(pos);
        }

        long[] positions(int kept) {
            int size = positions.size();
            if (kept >= size) return positions.toLongArray();
            var result = new long[kept];
            for (int i = 0; i < kept; i++) result[i] = positions.getLong((int) ((long) i * size / kept));
            return result;
        }
    }

    private static final class Findings {

        private final LinkedHashMap<GroupKey, Group> groups = new LinkedHashMap<>();
        private final HashSet<GroupKey> hidden = new HashSet<>();
        private int hiddenCount;
        private int total;

        void add(@Nullable StructureIssue issue) {
            if (issue == null) return;
            var type = issue.getType();
            var key = switch (keyKind(type)) {
                case 1 -> new GroupKey(type, issue.expected(), issue.found() == null || issue.found().isAir() ? null : Boolean.TRUE);
                case 2 -> new GroupKey(type, issue.counted(), null);
                case 3 -> new GroupKey(type, issue.subject(), null);
                case 4 -> new GroupKey(type, issue.param(), null);
                case 5 -> new GroupKey(type, null, null);
                default -> new GroupKey(type, issue.found() == null ? null : issue.found().getBlock(), null);
            };
            total++;
            var group = groups.get(key);
            if (group == null) {
                if (groups.size() >= MAX_ROWS - 1) {
                    hidden.add(key);
                    hiddenCount++;
                    return;
                }
                group = new Group(issue);
                groups.put(key, group);
            }
            group.count++;
            if (key.second() == Boolean.TRUE) {
                if (group.found == null) group.found = new Reference2IntOpenHashMap<>();
                group.found.addTo(issue.found().getBlock(), 1);
            }
            var pos = issue.getPos();
            if (pos != null) group.locate(pos.asLong());
        }

        private static int keyKind(StructureIssue.Type type) {
            if (type == StructureIssue.MISMATCH) return 1;
            if (type == StructureIssue.TOO_FEW || type == StructureIssue.TOO_MANY || type == StructureIssue.TOO_FEW_LAYER || type == StructureIssue.TOO_MANY_LAYER) return 2;
            if (type == StructureIssue.MIXED || type == StructureIssue.CONDITION) return 3;
            if (type == StructureIssue.SIZE) return 4;
            if (type == StructureIssue.SHARED || type == StructureIssue.UNLOADED) return 5;
            return 0;
        }

        int problems() {
            return total;
        }

        int groups() {
            return groups.size() + hidden.size();
        }

        List<Row> rows(@Nullable Reference2IntOpenHashMap<SimplePredicate> global) {
            var sorted = new ArrayList<>(groups.values());
            sorted.sort(Comparator.comparingInt((Group group) -> order(group.sample)).thenComparingInt(group -> -group.count));
            var kept = new int[sorted.size()];
            int budget = MAX_POSITIONS;
            for (int i = 0; i < kept.length; i++) {
                kept[i] = Math.min(sorted.get(i).positions.size(), MIN_ROW_POSITIONS);
                budget -= kept[i];
            }
            for (int i = 0; i < kept.length && budget > 0; i++) {
                int extra = Math.min(sorted.get(i).positions.size() - kept[i], budget);
                kept[i] += extra;
                budget -= extra;
            }
            var rows = new ArrayList<Row>(sorted.size() + 1);
            for (int i = 0; i < kept.length; i++) rows.add(row(sorted.get(i), kept[i], global));
            if (!hidden.isEmpty()) {
                rows.add(new Row(NORMAL, hiddenCount, Component.translatable("gtceu.structure.diagnosis.hidden", hidden.size()), count(hiddenCount), null, Items.AIR,
                        List.of(), new long[0]));
            }
            return Collections.unmodifiableList(rows);
        }

        private static int order(StructureIssue issue) {
            var type = issue.getType();
            for (int i = 0; i < ORDER.length; i++) {
                if (ORDER[i] == type) {
                    if (type == StructureIssue.MISMATCH && issue.found() != null && issue.found().isAir()) return ORDER.length;
                    return i;
                }
            }
            return ORDER.length + 1;
        }

        private static Row row(Group group, int kept, @Nullable Reference2IntOpenHashMap<SimplePredicate> global) {
            var issue = group.sample;
            var type = issue.getType();
            var positions = group.positions(kept);
            var lines = new ArrayList<Line>(4);
            Line subject = null;
            Component title;
            Component value = count(group.count);
            Item icon = Items.AIR;
            int severity = ERROR;
            if (type == StructureIssue.MISMATCH) {
                boolean missing = issue.found() == null || issue.found().isAir();
                title = missing ? MISSING : WRONG;
                var expected = issue.expected();
                if (expected != null) {
                    var compact = StructureIssue.compactLabel(expected);
                    var full = StructureIssue.label(expected);
                    if (compact != null) subject = new Line(EXPECTED, compact, full == null || full.equals(compact) ? Component.empty() : full);
                    icon = iconOf(expected);
                }
                if (!missing) lines.add(foundLine(group));
            } else if (issue.counted() != null) {
                var counted = issue.counted();
                boolean few = type == StructureIssue.TOO_FEW || type == StructureIssue.TOO_FEW_LAYER;
                boolean layered = type == StructureIssue.TOO_FEW_LAYER || type == StructureIssue.TOO_MANY_LAYER;
                title = few ? layered ? TOO_FEW_LAYER : TOO_FEW : layered ? TOO_MANY_LAYER : TOO_MANY;
                icon = iconOf(counted);
                var name = StructureIssue.label(counted);
                if (name != null) subject = new Line(SUBJECT, name, Component.empty());
                lines.add(new Line(EXPECTED, Component.translatable(few ? layered ? "gtceu.structure.diagnosis.at_least_layer" : "gtceu.structure.diagnosis.at_least" :
                        layered ? "gtceu.structure.diagnosis.at_most_layer" : "gtceu.structure.diagnosis.at_most", issue.limit()), Component.empty()));
                if (layered) {
                    value = Component.translatable("gtceu.structure.diagnosis.layers", group.count);
                } else if (!few && global == null) {
                    value = Component.empty();
                } else {
                    int actual = !few && global != null && global.containsKey(counted) ? global.getInt(counted) : issue.actual();
                    lines.add(new Line(FOUND, Component.translatable("gtceu.structure.diagnosis.amount", actual), Component.empty()));
                    value = Component.translatable(few ? "gtceu.structure.diagnosis.short_by" : "gtceu.structure.diagnosis.over_by", Math.abs(issue.limit() - actual));
                }
            } else if (type == StructureIssue.SIZE) {
                title = Component.translatable(type.key());
                var described = new ArrayList<Component>();
                issue.describe(described);
                if (described.size() > 1) lines.add(new Line(SIZE, described.get(described.size() - 1), Component.empty()));
                value = Component.empty();
            } else if (type == StructureIssue.MIXED) {
                title = Component.translatable(type.key(), issue.subject() == null ? Component.empty() : issue.subject());
            } else if (type == StructureIssue.CONDITION && issue.subject() != null) {
                title = issue.subject();
            } else {
                title = Component.translatable(type.key());
                if (type == StructureIssue.UNLOADED) severity = WARNING;
            }
            if (positions.length > 0) lines.add(positionLine(positions, group.count));
            else if (issue.counted() != null) lines.add(new Line(POSITION, POSITION_FREE, Component.empty()));
            return new Row(severity, group.count, title, value, subject, icon, List.copyOf(lines), positions);
        }

        private static Component count(int count) {
            return Component.translatable("gtceu.structure.diagnosis.count", count);
        }

        private static Line foundLine(Group group) {
            var found = group.found;
            if (found == null || found.isEmpty()) return new Line(FOUND, group.sample.found().getBlock().getName(), Component.empty());
            var blocks = new ArrayList<>(found.keySet());
            blocks.sort(Comparator.comparingInt((Block block) -> -found.getInt(block)));
            var first = blocks.get(0).getName();
            if (blocks.size() == 1) return new Line(FOUND, first, Component.empty());
            var value = Component.translatable("gtceu.structure.diagnosis.alternatives", first, blocks.size() - 1);
            var detail = Component.empty();
            int shown = Math.min(blocks.size(), MAX_DETAIL_KINDS);
            for (int i = 0; i < shown; i++) {
                if (i > 0) detail.append(SEPARATOR);
                detail.append(blocks.get(i).getName()).append(" ").append(count(found.getInt(blocks.get(i))));
            }
            if (blocks.size() > shown) detail = Component.translatable("gtceu.structure.diagnosis.kinds_more", detail, blocks.size());
            return new Line(FOUND, value, detail);
        }

        private static Line positionLine(long[] positions, int total) {
            var first = BlockPos.of(positions[0]);
            Component coords = Component.translatable("gtceu.structure.diagnosis.coords", first.getX(), first.getY(), first.getZ());
            Component value = total == 1 ? coords : Component.translatable("gtceu.structure.diagnosis.coords_etc", coords);
            var detail = Component.empty();
            int shown = Math.min(positions.length, MAX_DETAIL_POSITIONS);
            for (int i = 0; i < shown; i++) {
                var pos = BlockPos.of(positions[i]);
                if (i > 0) detail.append(SEPARATOR);
                detail.append(Component.translatable("gtceu.structure.diagnosis.coords", pos.getX(), pos.getY(), pos.getZ()));
            }
            if (total > shown) detail = Component.translatable("gtceu.structure.diagnosis.coords_more", detail, total);
            return new Line(POSITION, value, total == 1 ? Component.empty() : detail);
        }

        private static Item iconOf(TraceabilityPredicate predicate) {
            for (int rank = 0; rank < 3; rank++) {
                for (var simple : predicate.common) {
                    var item = StructureIssue.rank(simple) == rank ? iconOf(simple) : Items.AIR;
                    if (item != Items.AIR) return item;
                }
                for (var simple : predicate.limited) {
                    var item = StructureIssue.rank(simple) == rank ? iconOf(simple) : Items.AIR;
                    if (item != Items.AIR) return item;
                }
            }
            return Items.AIR;
        }

        private static Item iconOf(SimplePredicate simple) {
            if (simple.candidates == null) return Items.AIR;
            for (Block block : simple.candidates.get()) {
                var item = SimplePredicate.toItem(block);
                if (item != Items.AIR) return item;
            }
            return Items.AIR;
        }
    }
}
