package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

public final class Piece {

    private static final int PORT_POINT = 0;
    private static final int PORT_BEFORE = 1;
    private static final int PORT_AFTER = 2;

    private static volatile TraceabilityPredicate utilityAbilities;

    final RelativeDirection[] dirs;
    final Reference2ObjectArrayMap<PortKey, Port> ports;
    private final List<String[]> aisles;
    private final List<PortSpec> portSpecs;
    @Nullable
    private final Function<Size, Piece> sizer;

    private Piece(Function<Size, Piece> sizer) {
        this.sizer = sizer;
        this.dirs = new RelativeDirection[0];
        this.aisles = Collections.emptyList();
        this.portSpecs = Collections.emptyList();
        this.ports = new Reference2ObjectArrayMap<>();
    }

    private Piece(RelativeDirection[] dirs, List<String[]> aisles, List<PortSpec> portSpecs) {
        this.sizer = null;
        this.dirs = dirs;
        this.aisles = aisles;
        this.portSpecs = portSpecs;
        int[] charVec = Orientation.VEC[dirs[0].ordinal()];
        int[] stringVec = Orientation.VEC[dirs[1].ordinal()];
        int[] aisleVec = Orientation.VEC[dirs[2].ordinal()];
        this.ports = new Reference2ObjectArrayMap<>(portSpecs.size());
        for (var spec : portSpecs) {
            if (ports.put(spec.key, resolve(spec, charVec, stringVec, aisleVec)) != null) throw new IllegalStateException("duplicate port " + spec.key);
        }
    }

    private Port resolve(PortSpec spec, int[] charVec, int[] stringVec, int[] aisleVec) {
        int aisleDir = dirs[2].ordinal();
        return switch (spec.kind) {
            case PORT_BEFORE -> port(spec.key, new int[] { 0, 0, 0 }, Orientation.OPPOSITE[aisleDir]);
            case PORT_AFTER -> {
                int last = aisles.size() - 1;
                yield port(spec.key, new int[] { last * aisleVec[0], last * aisleVec[1], last * aisleVec[2] }, aisleDir);
            }
            default -> {
                var cells = new IntArrayList();
                for (int c = 0; c < aisles.size(); c++) {
                    var aisle = aisles.get(c);
                    for (int b = 0; b < aisle.length; b++) {
                        var row = aisle[b];
                        for (int a = 0; a < row.length(); a++) {
                            if (row.charAt(a) != spec.symbol) continue;
                            cells.add(a * charVec[0] + b * stringVec[0] + c * aisleVec[0]);
                            cells.add(a * charVec[1] + b * stringVec[1] + c * aisleVec[1]);
                            cells.add(a * charVec[2] + b * stringVec[2] + c * aisleVec[2]);
                        }
                    }
                }
                if (cells.isEmpty()) throw new IllegalStateException("port symbol '" + spec.symbol + "' does not appear in the piece");
                yield port(spec.key, cells.toIntArray(), spec.facing == null ? -1 : spec.facing.ordinal());
            }
        };
    }

    private static Port port(PortKey key, int[] cells, int facing) {
        return new Port(key, cells[0], cells[1], cells[2], facing, facing < 0 ? -1 : Orientation.upFor(facing), cells);
    }

    static TraceabilityPredicate utilityAbilities() {
        var predicate = utilityAbilities;
        if (predicate == null) {
            synchronized (Piece.class) {
                predicate = utilityAbilities;
                if (predicate == null) utilityAbilities = predicate = Predicates.utilityAbilities();
            }
        }
        return predicate;
    }

    public static Builder start(RelativeDirection charDir, RelativeDirection stringDir, RelativeDirection aisleDir) {
        return new Builder(charDir, stringDir, aisleDir);
    }

    public static Piece sized(Function<Size, Piece> sizer) {
        return new Piece(sizer);
    }

    boolean isSized() {
        return sizer != null;
    }

    Piece resolve(Size size) {
        if (sizer == null) return this;
        var piece = sizer.apply(size);
        if (piece.sizer != null) throw new IllegalStateException("sized piece must build a plain piece");
        return piece;
    }

    CompiledPiece compile(Symbols symbols) {
        if (sizer != null) throw new IllegalStateException("sized piece must be resolved before compiling");
        var pattern = FactoryBlockPattern.start(dirs[0], dirs[1], dirs[2]);
        for (var aisle : aisles) {
            for (var row : aisle) {
                for (int i = 0; i < row.length(); i++) {
                    char symbol = row.charAt(i);
                    if (symbol != ' ' && !symbols.has(symbol)) throw new IllegalStateException("symbol '" + symbol + "' is not defined in the structure symbols");
                }
            }
            pattern.aisle(aisle);
        }
        symbols.applyTo(pattern);
        return new CompiledPiece(this, pattern.build());
    }

    Port port(PortKey key) {
        if (sizer != null) throw new IllegalStateException("sized piece has no ports before it is resolved");
        var port = ports.get(key);
        if (port == null) throw new IllegalArgumentException("piece has no port " + key);
        return port;
    }

    char portSymbol(PortKey key) {
        for (var spec : portSpecs) {
            if (spec.key == key) return spec.symbol;
        }
        return '\0';
    }

    record Port(PortKey key, int x, int y, int z, int facing, int up, int[] cells) {

        int cellCount() {
            return cells.length / 3;
        }
    }

    private record PortSpec(PortKey key, int kind, char symbol, @Nullable RelativeDirection facing) {}

    public static final class Builder {

        private final RelativeDirection[] dirs;
        private final List<String[]> aisles = new ArrayList<>();
        private final List<PortSpec> ports = new ArrayList<>();
        private final HashMap<String, String> rows = new HashMap<>();

        private Builder(RelativeDirection charDir, RelativeDirection stringDir, RelativeDirection aisleDir) {
            this.dirs = new RelativeDirection[] { charDir, stringDir, aisleDir };
        }

        public Builder aisle(String... aisle) {
            var shared = new String[aisle.length];
            for (int i = 0; i < aisle.length; i++) shared[i] = rows.computeIfAbsent(aisle[i], row -> row);
            aisles.add(shared);
            return this;
        }

        public Builder portBefore(PortKey key) {
            ports.add(new PortSpec(key, PORT_BEFORE, '\0', null));
            return this;
        }

        public Builder portAfter(PortKey key) {
            ports.add(new PortSpec(key, PORT_AFTER, '\0', null));
            return this;
        }

        public Builder port(char symbol, PortKey key, RelativeDirection facing) {
            ports.add(new PortSpec(key, PORT_POINT, symbol, facing));
            return this;
        }

        public Builder port(char symbol, PortKey key) {
            ports.add(new PortSpec(key, PORT_POINT, symbol, null));
            return this;
        }

        public Piece build() {
            if (aisles.isEmpty()) throw new IllegalStateException("piece has no aisle");
            return new Piece(dirs, List.copyOf(aisles), List.copyOf(ports));
        }
    }
}
