package com.gregtechceu.gtceu.api.machine.multiblockpro;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract sealed class Slot permits Slot.PieceSlot, Slot.ChoiceSlot, Slot.MachineSlot {

    @Nullable
    ParamKey param;
    final List<Binding> children = new ArrayList<>();

    private Slot() {}

    public static PieceSlot one(Piece piece, PortKey in) {
        return new PieceSlot(piece, in, null, null, 1, 1);
    }

    public static PieceSlot optional(Piece piece, PortKey in) {
        return new PieceSlot(piece, in, null, null, 0, 1);
    }

    public static PieceSlot chain(Piece piece, PortKey in, PortKey reflexiveOut, PortKey reflexiveIn) {
        return new PieceSlot(piece, in, reflexiveOut, reflexiveIn, -1, -1);
    }

    public static MachineSlot machines(MachineProtocol protocol) {
        return new MachineSlot(protocol);
    }

    public static ChoiceSlot choice(Slot... options) {
        if (options.length == 0) throw new IllegalArgumentException("choice needs at least one option");
        return new ChoiceSlot(options);
    }

    abstract void validate();

    static void attachable(Piece.Port port) {
        if (port.cellCount() != 1 || port.facing() < 0) throw new IllegalStateException("port " + port.key() + " attaches a piece and needs exactly one cell and a facing");
    }

    record Binding(PortKey port, Slot slot) {}

    public static final class PieceSlot extends Slot {

        final Piece piece;
        final PortKey in;
        @Nullable
        final PortKey reflexiveOut;
        @Nullable
        final PortKey reflexiveIn;
        int min;
        int max;
        boolean backtrack;

        private PieceSlot(Piece piece, PortKey in, @Nullable PortKey reflexiveOut, @Nullable PortKey reflexiveIn, int min, int max) {
            this.piece = piece;
            this.in = in;
            this.reflexiveOut = reflexiveOut;
            this.reflexiveIn = reflexiveIn;
            this.min = min;
            this.max = max;
        }

        public PieceSlot count(ParamKey param) {
            this.param = param;
            return this;
        }

        public PieceSlot count(ParamKey param, int min, int max) {
            if (min < 0 || max < 1 || min > max) throw new IllegalArgumentException("invalid count range " + min + ".." + max);
            this.param = param;
            this.min = min;
            this.max = max;
            return this;
        }

        public PieceSlot backtrack() {
            this.backtrack = true;
            return this;
        }

        public PieceSlot atPort(PortKey port, Slot slot) {
            children.add(new Binding(port, slot));
            return this;
        }

        boolean repeats() {
            return reflexiveOut != null && reflexiveIn != null;
        }

        @Override
        void validate() {
            if (min < 0) throw new IllegalStateException("chain slot needs count(param, min, max)");
            if (!repeats() && max > 1) throw new IllegalStateException("slot repeating more than once needs reflexive ports");
            if (!piece.isSized()) validatePorts(piece);
        }

        void validatePorts(Piece template) {
            attachable(template.port(in));
            if (repeats()) {
                attachable(template.port(reflexiveOut));
                attachable(template.port(reflexiveIn));
            }
            for (var child : children) {
                var port = template.port(child.port());
                if (!(child.slot() instanceof MachineSlot)) attachable(port);
            }
        }
    }

    public static final class MachineSlot extends Slot {

        final MachineProtocol protocol;

        private MachineSlot(MachineProtocol protocol) {
            this.protocol = protocol;
        }

        @Override
        void validate() {
            if (!children.isEmpty()) throw new IllegalStateException("machine slots have no children");
        }
    }

    public static final class ChoiceSlot extends Slot {

        final Slot[] options;
        boolean optional;

        private ChoiceSlot(Slot[] options) {
            this.options = options;
        }

        public ChoiceSlot optional() {
            this.optional = true;
            return this;
        }

        public ChoiceSlot count(ParamKey param) {
            this.param = param;
            return this;
        }

        @Override
        void validate() {
            if (!children.isEmpty()) throw new IllegalStateException("attach children to the options of a choice");
        }
    }
}
