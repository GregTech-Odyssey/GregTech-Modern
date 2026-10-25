package com.gregtechceu.gtceu.api.machine.multiblockpro;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

/** Default dimensions without compiling predicates or expanding pieces into layout cells. */
final class StructureDimensions {

    private final Structure structure;
    private final int[] values;
    private final Size size;
    private final Reference2ObjectOpenHashMap<Piece, Geometry> pieces;
    private final int[] bounds = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };

    StructureDimensions(Structure structure, int[] values, Size size) {
        this.structure = structure;
        this.values = values;
        this.size = size;
        this.pieces = new Reference2ObjectOpenHashMap<>(structure.slots.length);
    }

    int[] compute() {
        // Centering on the controller only translates the whole assembly, leaving dimensions unchanged.
        var root = new Pose(geometry(structure.root), 0, 0, 0, 0);
        include(root);
        var children = structure.childNodes[0];
        var ports = structure.childPorts[0];
        for (int i = 0; i < children.length; i++) walk(root, root.geometry.piece.port(ports[i]), children[i]);
        return bounds[0] == Integer.MAX_VALUE ? new int[3] :
                new int[] { bounds[3] - bounds[0] + 1, bounds[4] - bounds[1] + 1, bounds[5] - bounds[2] + 1 };
    }

    private Geometry geometry(Piece template) {
        var geometry = pieces.get(template);
        if (geometry == null) {
            var piece = template.resolve(size);
            geometry = new Geometry(piece, piece.bounds(structure.symbols));
            pieces.put(template, geometry);
        }
        return geometry;
    }

    private void walk(Pose host, Piece.Port port, int node) {
        int option = structure.slotOptions[node];
        var slot = structure.slots[node];
        if (slot instanceof Slot.PieceSlot pieceSlot) {
            int count = option >= 0 ? values[option] : pieceSlot.min;
            var children = structure.childNodes[node];
            var ports = structure.childPorts[node];
            if (count == 0) {
                for (int i = 0; i < children.length; i++) {
                    if (ports[i] == pieceSlot.reflexiveOut) walk(host, port, children[i]);
                }
                return;
            }
            var geometry = geometry(pieceSlot.piece);
            var piece = geometry.piece;
            var reflexiveOut = pieceSlot.reflexiveOut == null ? null : piece.port(pieceSlot.reflexiveOut);
            var reflexiveIn = pieceSlot.reflexiveIn == null ? null : piece.port(pieceSlot.reflexiveIn);
            var previous = host;
            var previousPort = port;
            var enterPort = piece.port(pieceSlot.in);
            Pose last = null;
            for (int i = 0; i < count; i++) {
                var placement = previous.attach(previousPort, geometry, enterPort);
                if (placement == null) return;
                include(placement);
                last = placement;
                if (reflexiveOut == null || reflexiveIn == null) break;
                previous = placement;
                previousPort = reflexiveOut;
                enterPort = reflexiveIn;
            }
            for (int i = 0; i < children.length; i++) walk(last, piece.port(ports[i]), children[i]);
        } else if (slot instanceof Slot.ChoiceSlot) {
            int chosen = option >= 0 ? values[option] : 1;
            if (chosen > 0) walk(host, port, structure.childNodes[node][chosen - 1]);
        }
    }

    private void include(Pose pose) {
        var local = pose.geometry.bounds;
        if (local[0] == Integer.MAX_VALUE) return;
        var rotation = Orientation.ROT[pose.rot];
        for (int axis = 0; axis < 3; axis++) {
            int offset = axis == 0 ? pose.x : axis == 1 ? pose.y : pose.z;
            int row = axis * 3;
            int low = offset + rotation[row] * local[0] + rotation[row + 1] * local[1] + rotation[row + 2] * local[2];
            int high = offset + rotation[row] * local[3] + rotation[row + 1] * local[4] + rotation[row + 2] * local[5];
            bounds[axis] = Math.min(bounds[axis], Math.min(low, high));
            bounds[axis + 3] = Math.max(bounds[axis + 3], Math.max(low, high));
        }
    }

    private record Geometry(Piece piece, int[] bounds) {}

    private record Pose(Geometry geometry, int rot, int x, int y, int z) {

        @Nullable
        Pose attach(Piece.Port hostPort, Geometry child, Piece.Port childPort) {
            int[] m = Orientation.ROT[rot];
            int[] dirs = Orientation.ROT_DIR[rot];
            int facing = dirs[hostPort.facing()];
            int r = Orientation.align(childPort.facing(), childPort.up(), Orientation.OPPOSITE[facing], dirs[hostPort.up()]);
            if (r < 0) return null;
            int[] step = Orientation.VEC[facing];
            int tx = x + m[0] * hostPort.x() + m[1] * hostPort.y() + m[2] * hostPort.z() + step[0];
            int ty = y + m[3] * hostPort.x() + m[4] * hostPort.y() + m[5] * hostPort.z() + step[1];
            int tz = z + m[6] * hostPort.x() + m[7] * hostPort.y() + m[8] * hostPort.z() + step[2];
            int[] c = Orientation.ROT[r];
            return new Pose(child, r,
                    tx - (c[0] * childPort.x() + c[1] * childPort.y() + c[2] * childPort.z()),
                    ty - (c[3] * childPort.x() + c[4] * childPort.y() + c[5] * childPort.z()),
                    tz - (c[6] * childPort.x() + c[7] * childPort.y() + c[8] * childPort.z()));
        }
    }
}
