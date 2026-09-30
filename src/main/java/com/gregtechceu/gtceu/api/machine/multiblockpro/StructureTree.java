package com.gregtechceu.gtceu.api.machine.multiblockpro;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class StructureTree {

    public enum Kind {
        ROOT,
        FIXED,
        TOGGLE,
        COUNT,
        CHOICE,
        MACHINES
    }

    public record Node(int parent, Kind kind, int option, @Nullable ParamKey key, int min, int max, int branch, boolean reflexive,
                       int[] children, @Nullable MachineProtocol protocol) {

        public boolean isOptionalModule() {
            return kind == Kind.TOGGLE || kind == Kind.CHOICE && min == 0;
        }
    }

    private final Node[] nodes;
    private final int[] sizes;

    StructureTree(List<Node> nodes, int[] sizes) {
        this.nodes = nodes.toArray(new Node[0]);
        this.sizes = sizes;
    }

    public Node[] nodes() {
        return nodes;
    }

    public Node root() {
        return nodes[0];
    }

    public int[] sizes() {
        return sizes;
    }

    public boolean active(int index, int[] values) {
        var node = nodes[index];
        if (node.parent() < 0) return true;
        var parent = nodes[node.parent()];
        if (!active(node.parent(), values)) return false;
        return switch (parent.kind()) {
            case CHOICE -> value(node.parent(), values) == node.branch() + 1;
            case FIXED, TOGGLE, COUNT -> node.reflexive() || value(node.parent(), values) > 0;
            case ROOT, MACHINES -> true;
        };
    }

    public int value(int index, int[] values) {
        var node = nodes[index];
        if (node.option() >= 0 && node.option() < values.length) return values[node.option()];
        return node.kind() == Kind.CHOICE ? 1 : node.min();
    }
}
