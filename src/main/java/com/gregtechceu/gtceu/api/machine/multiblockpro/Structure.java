package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.PredicateDirections;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public final class Structure {

    private static final Size NO_SIZE = new Size(new ParamKey[0], new int[0]);
    private static final int RESOLVED_LIMIT = 64;
    private static final int[][] NO_CHILDREN = new int[0][];
    private static final int[][] MIRRORS = mirrors();
    static final Set<Class<?>> WHITELIST = new ReferenceOpenHashSet<>();

    final Piece root;
    final List<Slot.Binding> bindings;
    final boolean multiPiece;
    final Measure[] measures;
    final ParamKey[] measureKeys;
    @Nullable
    final Predicate<Size> requirement;
    final boolean perSize;
    final boolean optionalSlots;
    final Symbols symbols;
    final Slot[] slots;
    final int[] slotOptions;
    final int[][] childNodes;
    final PortKey[][] childPorts;
    @Nullable
    private volatile CompiledStructure concreteDefault;
    @Nullable
    private volatile int[] dimensions;
    @Nullable
    private volatile Boolean mirrorSymmetric;
    private final List<Limit> limits;
    private final List<Option> options;
    private final StructureTree tree;
    private final int optionalModuleCount;
    private final MachineProtocol[] protocols;
    private final LinkedHashMap<Size, CompiledStructure> resolved = new LinkedHashMap<>(16, 0.75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<Size, CompiledStructure> eldest) {
            return size() > RESOLVED_LIMIT;
        }
    };

    private Structure(Builder builder) {
        if (builder.symbols == null) throw new IllegalStateException("structure needs symbols(...)");
        this.symbols = builder.symbols.copy();
        this.root = builder.root;
        this.bindings = List.copyOf(builder.bindings);
        this.multiPiece = !bindings.isEmpty();
        this.limits = List.copyOf(builder.limits);
        this.requirement = builder.requirement;
        var index = new Index(symbols, builder.measures);
        index.root(root, bindings);
        this.measures = new Measure[builder.measures.size()];
        for (int i = 0; i < measures.length; i++) {
            var spec = builder.measures.get(i);
            var until = spec.untilPredicate != null ? spec.untilPredicate : symbols.get(spec.untilSymbol);
            measures[i] = new Measure(spec.param, spec.toward.ordinal(), until, spec.min, spec.max);
        }
        this.measureKeys = new ParamKey[measures.length];
        for (int i = 0; i < measures.length; i++) measureKeys[i] = measures[i].key();
        if (!root.isSized()) validateRootPorts(root);
        if (index.sized && measures.length == 0) throw new IllegalStateException("sized pieces need at least one measure");
        this.slots = index.slots.toArray(new Slot[0]);
        this.slotOptions = index.slotOptions.toIntArray();
        this.childNodes = index.childNodes.toArray(NO_CHILDREN);
        this.childPorts = index.childPorts.toArray(new PortKey[0][]);
        this.options = List.copyOf(index.options);
        this.optionalSlots = index.optional;
        var sizes = new int[measures.length];
        for (int i = 0; i < sizes.length; i++) sizes[i] = i;
        this.tree = new StructureTree(index.nodes, sizes);
        int modules = 0;
        for (var node : index.nodes) {
            if (node.isOptionalModule()) modules++;
        }
        this.optionalModuleCount = modules;
        this.protocols = index.protocols.toArray(new MachineProtocol[0]);
        this.perSize = measures.length > 0 && (index.sized || !limits.isEmpty());
    }

    CompiledStructure concreteDefault() {
        var concrete = concreteDefault;
        if (concrete != null) return concrete;
        synchronized (this) {
            concrete = concreteDefault;
            if (concrete == null) {
                if (perSize) {
                    var minimum = new int[measures.length];
                    for (int i = 0; i < minimum.length; i++) minimum[i] = measures[i].min();
                    concrete = forSize(new Size(measureKeys, minimum));
                } else {
                    concrete = new CompiledStructure(this, NO_SIZE);
                }
                concreteDefault = concrete;
            }
        }
        return concrete;
    }

    public Collection<TraceabilityPredicate> predicates() {
        return concreteDefault().predicates;
    }

    void validateRootPorts(Piece template) {
        for (var binding : bindings) {
            var port = template.port(binding.port());
            if (!(binding.slot() instanceof Slot.MachineSlot)) Slot.attachable(port);
        }
    }

    int limitCount() {
        return limits.size();
    }

    SimplePredicate[] limitPredicates(int index) {
        return limits.get(index).predicates();
    }

    int limitMax(int index, Size size) {
        return limits.get(index).max().applyAsInt(size);
    }

    public List<Option> options() {
        return options;
    }

    public StructureTree tree() {
        return tree;
    }

    public int optionalModuleCount() {
        return optionalModuleCount;
    }

    public int[] defaultValues() {
        var values = new int[options.size()];
        for (int i = 0; i < values.length; i++) values[i] = options.get(i).defaultValue();
        return values;
    }

    public int[] minimumValues() {
        var values = new int[options.size()];
        for (int i = 0; i < values.length; i++) values[i] = options.get(i).min();
        return values;
    }

    public int[] valuesOf(Assembly assembly) {
        var values = defaultValues();
        for (int i = 0; i < values.length; i++) {
            var option = options.get(i);
            if (option.key() == null) continue;
            int value = assembly.get(option.key());
            if (value >= option.min() && value <= option.max()) values[i] = value;
        }
        return accepts(values) ? values : defaultValues();
    }

    public boolean accepts(int[] values) {
        if (values.length != options.size()) return false;
        for (int i = 0; i < values.length; i++) {
            var option = options.get(i);
            if (values[i] < option.min() || values[i] > option.max()) return false;
        }
        return requirement == null || requirement.test(sizeOf(values));
    }

    private Size sizeOf(int[] values) {
        var sizes = new int[measures.length];
        System.arraycopy(values, 0, sizes, 0, measures.length);
        return new Size(measureKeys, sizes);
    }

    @Nullable
    public Layout layout(int[] values) {
        if (!accepts(values)) return null;
        return buildLayout(values.clone());
    }

    private Layout buildLayout(int[] values) {
        var concrete = measures.length == 0 ? concreteDefault() : forSize(sizeOf(values));
        var cells = new Cells();
        var host = Placement.root(concrete.root);
        emit(host, cells, 0);
        var children = childNodes[0];
        var ports = childPorts[0];
        for (int i = 0; i < children.length; i++) {
            walk(concrete, values, host, host.piece.port(ports[i]), children[i], cells);
        }
        var list = new ArrayList<Layout.Cell>(cells.predicates.size());
        for (var it = cells.predicates.long2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            long pos = entry.getLongKey();
            list.add(new Layout.Cell(BlockPos.getX(pos), BlockPos.getY(pos), BlockPos.getZ(pos), entry.getValue(), cells.layers.get(pos), cells.nodes.get(pos)));
        }
        return new Layout(List.copyOf(list), this);
    }

    private void walk(CompiledStructure concrete, int[] values, Placement host, Piece.Port port, int node, Cells cells) {
        int option = slotOptions[node];
        var slot = slots[node];
        if (slot instanceof Slot.PieceSlot pieceSlot) {
            var piece = concrete.pieces[node];
            int count = option >= 0 ? values[option] : pieceSlot.min;
            var reflexiveOut = pieceSlot.reflexiveOut == null ? null : piece.port(pieceSlot.reflexiveOut);
            var reflexiveIn = pieceSlot.reflexiveIn == null ? null : piece.port(pieceSlot.reflexiveIn);
            var previous = host;
            var previousPort = port;
            var enterPort = piece.port(pieceSlot.in);
            Placement last = null;
            for (int i = 0; i < count; i++) {
                var placement = previous.attach(previousPort, piece, enterPort);
                if (placement == null) return;
                emit(placement, cells, node);
                last = placement;
                if (reflexiveOut == null || reflexiveIn == null) break;
                previous = placement;
                previousPort = reflexiveOut;
                enterPort = reflexiveIn;
            }
            var children = childNodes[node];
            var ports = childPorts[node];
            if (last == null) {
                for (int i = 0; i < children.length; i++) {
                    if (ports[i] == pieceSlot.reflexiveOut) walk(concrete, values, host, port, children[i], cells);
                }
                return;
            }
            for (int i = 0; i < children.length; i++) walk(concrete, values, last, last.piece.port(ports[i]), children[i], cells);
        } else if (slot instanceof Slot.ChoiceSlot) {
            int chosen = option >= 0 ? values[option] : 1;
            if (chosen > 0) walk(concrete, values, host, port, childNodes[node][chosen - 1], cells);
        }
    }

    private static void emit(Placement placement, Cells cells, int node) {
        var piece = placement.piece;
        int i = 0;
        for (int aisle = 0; aisle < piece.aisleCount; aisle++) {
            int layer = cells.nextLayer++;
            for (int end = piece.aisleEnd[aisle]; i < end; i++) {
                long pos = BlockPos.asLong(placement.relX(i), placement.relY(i), placement.relZ(i));
                if (cells.predicates.putIfAbsent(pos, piece.predicate(i)) == null) {
                    cells.layers.put(pos, layer);
                    cells.nodes.put(pos, node);
                }
            }
        }
    }

    private static final class Cells {

        final Long2ObjectLinkedOpenHashMap<TraceabilityPredicate> predicates = new Long2ObjectLinkedOpenHashMap<>();
        final Long2IntOpenHashMap layers = new Long2IntOpenHashMap();
        final Long2IntOpenHashMap nodes = new Long2IntOpenHashMap();
        int nextLayer;
    }

    CompiledStructure forSize(Size size) {
        if (!perSize) return concreteDefault();
        synchronized (resolved) {
            var cached = resolved.get(size);
            if (cached != null) return cached;
        }
        var created = new CompiledStructure(this, size);
        synchronized (resolved) {
            var cached = resolved.putIfAbsent(size, created);
            return cached != null ? cached : created;
        }
    }

    public static Builder root(Piece root) {
        return new Builder(root);
    }

    public static void addWhitelistBlockEntity(Class<?> clazz) {
        WHITELIST.add(clazz);
    }

    public Structure bind(MultiblockMachineDefinition definition) {
        for (var protocol : protocols) protocol.addHost(definition);
        return this;
    }

    public boolean match(MultiblockState state, boolean save) {
        var controller = state.controller;
        var center = state.controllerPos;
        var front = controller.self().getFrontFacing();
        var facings = controller.hasFrontFacing() ? new Direction[] { front } : new Direction[] { Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST };
        var up = controller.self().getUpwardsFacing();
        boolean allowsFlip = controller.self().allowFlip();
        state.errorRecord.clear();
        for (var facing : facings) {
            if (matchAt(state, center, facing, up, false, save)) return true;
            if (!save) state.errorRecord.add(state.error);
            if (allowsFlip) return matchAt(state, center, facing, up, true, save);
        }
        return false;
    }

    private boolean matchAt(MultiblockState state, BlockPos center, Direction front, Direction up, boolean flip, boolean save) {
        boolean result = StructureMatcher.acquire().match(this, state, center, front, up, flip, save);
        if (result && !flip && optionalSlots && fuller(state, center, front, up)) {
            return StructureMatcher.acquire().match(this, state, center, front, up, true, save);
        }
        return result;
    }

    private boolean fuller(MultiblockState state, BlockPos center, Direction front, Direction up) {
        if (mirrorSymmetric()) return false;
        var assembly = state.getMatchContext().get(Assembly.KEY);
        if (assembly == null || assembly.getOptionalMissing() == 0 || !state.controller.self().allowFlip()) return false;
        var probe = MultiblockState.probe(state);
        if (!StructureMatcher.acquire().match(this, probe, center, front, up, true, false)) return false;
        var flipped = probe.getMatchContext().get(Assembly.KEY);
        return flipped != null && flipped.getOptionalFormed() > assembly.getOptionalFormed();
    }

    private int[] dimensions() {
        var result = dimensions;
        if (result == null) {
            var layout = layout(defaultValues());
            result = layout == null ? new int[3] : new int[] { layout.width(), layout.height(), layout.depth() };
            dimensions = result;
        }
        return result;
    }

    public int getWidth() {
        return dimensions()[0];
    }

    public int getHeight() {
        return dimensions()[1];
    }

    public int getDepth() {
        return dimensions()[2];
    }

    private boolean mirrorSymmetric() {
        var result = mirrorSymmetric;
        if (result == null) {
            result = computeMirrorSymmetric();
            mirrorSymmetric = result;
        }
        return result;
    }

    private boolean computeMirrorSymmetric() {
        if (!optionalSlots || perSize) return false;
        var values = defaultValues();
        for (var node : tree.nodes()) {
            int option = node.option();
            if (option < 0 || option >= values.length) continue;
            if (node.kind() == StructureTree.Kind.CHOICE && node.max() > 1) return false;
            if (node.isOptionalModule()) values[option] = 1;
        }
        var layout = layout(values);
        if (layout == null) return false;
        var cells = layout.cells();
        var index = new Long2IntOpenHashMap(cells.size());
        index.defaultReturnValue(-1);
        for (int i = 0; i < cells.size(); i++) {
            var cell = cells.get(i);
            if (directional(cell.predicate())) return false;
            index.put(BlockPos.asLong(cell.x(), cell.y(), cell.z()), i);
        }
        for (var m : MIRRORS) {
            for (var cell : cells) {
                int x = m[0] * cell.x() + m[1] * cell.y() + m[2] * cell.z();
                int y = m[3] * cell.x() + m[4] * cell.y() + m[5] * cell.z();
                int z = m[6] * cell.x() + m[7] * cell.y() + m[8] * cell.z();
                int j = index.get(BlockPos.asLong(x, y, z));
                if (j < 0) return false;
                var other = cells.get(j);
                if (other.predicate() != cell.predicate() || other.node() != cell.node()) return false;
            }
        }
        return true;
    }

    private static boolean directional(TraceabilityPredicate predicate) {
        if (predicate.direction != GTUtil.NULL_FUNCTION) return true;
        for (var simple : predicate.common) {
            if (simple instanceof PredicateDirections) return true;
        }
        for (var simple : predicate.limited) {
            if (simple instanceof PredicateDirections) return true;
        }
        return false;
    }

    private static int[][] mirrors() {
        List<int[]> mirrors = new ArrayList<>();
        for (var front : Direction.values()) {
            for (var up : Direction.Plane.HORIZONTAL) {
                int[] plain = Orientation.world(front, up, false);
                int[] flipped = Orientation.world(front, up, true);
                int[] m = new int[9];
                for (int row = 0; row < 3; row++) {
                    for (int column = 0; column < 3; column++) {
                        m[row * 3 + column] = plain[row * 3] * flipped[column * 3] + plain[row * 3 + 1] * flipped[column * 3 + 1] +
                                plain[row * 3 + 2] * flipped[column * 3 + 2];
                    }
                }
                boolean known = false;
                for (var existing : mirrors) known |= Arrays.equals(existing, m);
                if (!known) mirrors.add(m);
            }
        }
        return mirrors.toArray(int[][]::new);
    }

    public record Option(Kind kind, @Nullable ParamKey key, int min, int max, int defaultValue) {

        public enum Kind {
            SIZE,
            COUNT,
            CHOICE
        }
    }

    record Measure(ParamKey key, int direction, TraceabilityPredicate until, int min, int max) {}

    private record Limit(SimplePredicate[] predicates, ToIntFunction<Size> max) {}

    private static final class Index {

        final Symbols symbols;
        final ArrayList<Slot> slots = new ArrayList<>();
        final IntArrayList slotOptions = new IntArrayList();
        final ArrayList<int[]> childNodes = new ArrayList<>();
        final ArrayList<PortKey[]> childPorts = new ArrayList<>();
        final ArrayList<StructureTree.Node> nodes = new ArrayList<>();
        final ArrayList<Option> options = new ArrayList<>();
        final ReferenceLinkedOpenHashSet<MachineProtocol> protocols = new ReferenceLinkedOpenHashSet<>();
        final Char2ObjectOpenHashMap<MachineProtocol> machineSymbols = new Char2ObjectOpenHashMap<>();
        boolean optional;
        boolean sized;

        Index(Symbols symbols, List<MeasureBuilder> measures) {
            this.symbols = symbols;
            for (var measure : measures) options.add(new Option(Option.Kind.SIZE, measure.param, measure.min, measure.max, measure.min));
        }

        void root(Piece root, List<Slot.Binding> bindings) {
            sized = root.isSized();
            int index = reserve(null, -1);
            var children = new int[bindings.size()];
            var ports = new PortKey[bindings.size()];
            for (int i = 0; i < children.length; i++) {
                var binding = bindings.get(i);
                ports[i] = binding.port();
                children[i] = visit(index, root, binding.port(), binding.slot(), -1, false);
            }
            finish(index, new StructureTree.Node(-1, StructureTree.Kind.ROOT, -1, null, 0, 0, -1, false, children, null), children, ports);
        }

        private int visit(int parent, Piece host, PortKey port, Slot slot, int branch, boolean reflexive) {
            slot.validate();
            if (slot instanceof Slot.PieceSlot pieceSlot) {
                sized |= pieceSlot.piece.isSized();
                int option = -1;
                if (pieceSlot.min != pieceSlot.max) {
                    boolean toggle = pieceSlot.min == 0 && pieceSlot.max == 1;
                    optional |= toggle;
                    option = options.size();
                    options.add(new Option(Option.Kind.COUNT, slot.param, pieceSlot.min, pieceSlot.max,
                            toggle ? 0 : Math.min(pieceSlot.max, Math.max(pieceSlot.min, 1))));
                }
                int index = reserve(slot, option);
                var children = new int[pieceSlot.children.size()];
                var ports = new PortKey[children.length];
                for (int i = 0; i < children.length; i++) {
                    var child = pieceSlot.children.get(i);
                    ports[i] = child.port();
                    children[i] = visit(index, pieceSlot.piece, child.port(), child.slot(), -1, child.port() == pieceSlot.reflexiveOut);
                }
                var kind = pieceSlot.min == pieceSlot.max ? StructureTree.Kind.FIXED :
                        pieceSlot.min == 0 && pieceSlot.max == 1 ? StructureTree.Kind.TOGGLE : StructureTree.Kind.COUNT;
                finish(index, new StructureTree.Node(parent, kind, option, slot.param, pieceSlot.min, pieceSlot.max, branch, reflexive, children, null),
                        children, ports);
                return index;
            }
            if (slot instanceof Slot.ChoiceSlot choice) {
                optional |= choice.optional;
                int option = options.size();
                int min = choice.optional ? 0 : 1;
                options.add(new Option(Option.Kind.CHOICE, slot.param, min, choice.options.length, min));
                int index = reserve(slot, option);
                var children = new int[choice.options.length];
                for (int i = 0; i < children.length; i++) children[i] = visit(index, host, port, choice.options[i], i, false);
                finish(index, new StructureTree.Node(parent, StructureTree.Kind.CHOICE, option, slot.param, min, choice.options.length, branch, reflexive, children, null),
                        children, null);
                return index;
            }
            var machines = (Slot.MachineSlot) slot;
            defineMachinePort(host, port, machines.protocol);
            protocols.add(machines.protocol);
            int index = reserve(slot, -1);
            var children = new int[0];
            finish(index, new StructureTree.Node(parent, StructureTree.Kind.MACHINES, -1, null, 0, 0, branch, reflexive, children, machines.protocol),
                    children, null);
            return index;
        }

        private int reserve(@Nullable Slot slot, int option) {
            int index = slots.size();
            slots.add(slot);
            slotOptions.add(option);
            childNodes.add(null);
            childPorts.add(null);
            nodes.add(null);
            return index;
        }

        private void finish(int index, StructureTree.Node node, int[] children, PortKey[] ports) {
            nodes.set(index, node);
            childNodes.set(index, children);
            childPorts.set(index, ports);
        }

        private void defineMachinePort(Piece host, PortKey port, MachineProtocol protocol) {
            char symbol = host.portSymbol(port);
            if (symbol == '\0') return;
            var bound = machineSymbols.get(symbol);
            if (bound != null) {
                if (bound != protocol) throw new IllegalStateException("port symbol '" + symbol + "' is used by protocols " + bound + " and " + protocol);
                return;
            }
            if (symbols.has(symbol)) return;
            machineSymbols.put(symbol, protocol);
            symbols.where(symbol, protocol.cell());
        }
    }

    public static final class MeasureBuilder {

        @Nullable
        private ParamKey param;
        @Nullable
        private RelativeDirection toward;
        @Nullable
        private TraceabilityPredicate untilPredicate;
        private char untilSymbol;
        private boolean hasUntil;
        private int min = -1;
        private int max = -1;

        private MeasureBuilder() {}

        public MeasureBuilder param(ParamKey param) {
            this.param = param;
            return this;
        }

        public MeasureBuilder toward(RelativeDirection direction) {
            this.toward = direction;
            return this;
        }

        public MeasureBuilder until(char symbol) {
            this.untilSymbol = symbol;
            this.untilPredicate = null;
            this.hasUntil = true;
            return this;
        }

        public MeasureBuilder until(TraceabilityPredicate edge) {
            this.untilPredicate = edge;
            this.hasUntil = true;
            return this;
        }

        public MeasureBuilder range(int min, int max) {
            this.min = min;
            this.max = max;
            return this;
        }

        private void validate() {
            if (param == null || toward == null || !hasUntil) throw new IllegalStateException("measure needs param, toward and until");
            if (min < 1 || max < min) throw new IllegalStateException("invalid measure range " + min + ".." + max);
        }
    }

    public static final class Builder {

        private final Piece root;
        private final List<Slot.Binding> bindings = new ArrayList<>();
        private final List<MeasureBuilder> measures = new ArrayList<>();
        private final List<Limit> limits = new ArrayList<>();
        @Nullable
        private Symbols symbols;
        @Nullable
        private Predicate<Size> requirement;

        private Builder(Piece root) {
            this.root = root;
        }

        public Builder symbols(Symbols symbols) {
            this.symbols = symbols;
            return this;
        }

        public Builder atPort(PortKey port, Slot slot) {
            bindings.add(new Slot.Binding(port, slot));
            return this;
        }

        public Builder measure(Consumer<MeasureBuilder> spec) {
            var measure = new MeasureBuilder();
            spec.accept(measure);
            measure.validate();
            for (var existing : measures) {
                if (existing.param == measure.param) throw new IllegalStateException("measured twice: " + measure.param);
            }
            measures.add(measure);
            return this;
        }

        public Builder require(Predicate<Size> requirement) {
            this.requirement = requirement;
            return this;
        }

        public Builder limit(TraceabilityPredicate predicate, ToIntFunction<Size> max) {
            if (limits.size() == Integer.SIZE) throw new IllegalStateException("too many limits");
            var simples = new ArrayList<SimplePredicate>(predicate.common);
            simples.addAll(predicate.limited);
            for (var simple : simples) {
                if (simple.minCount != -1 || simple.maxCount != -1) throw new IllegalArgumentException("limit() predicate must not use setMin/MaxGlobalLimited");
            }
            limits.add(new Limit(simples.toArray(SimplePredicate[]::new), max));
            return this;
        }

        public Structure build() {
            return new Structure(this);
        }
    }
}
