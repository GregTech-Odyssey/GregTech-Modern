package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.ControllerPredicate;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;

import java.util.ArrayList;
import java.util.List;

final class CompiledPiece {

    final Piece template;
    final int aisleCount;
    final TraceabilityPredicate[] palette;
    final int[] aisleEnd;
    private final int size;
    private final byte[] smallIndex;
    private final short[] largeIndex;
    private final int[] packed;
    private final int[] x;
    private final int[] y;
    private final int[] z;
    private final int shiftX, shiftY, maskY, maskZ;
    final int[] bounds;
    final int controller;

    CompiledPiece(Piece template, BlockPattern compiled) {
        this.template = template;
        var dirs = template.dirs;
        var grid = compiled.blockMatches;
        this.aisleCount = compiled.fingerLength;
        int height = compiled.thumbLength;
        int width = compiled.palmLength;
        int[] charVec = Orientation.VEC[dirs[0].ordinal()];
        int[] stringVec = Orientation.VEC[dirs[1].ordinal()];
        int[] aisleVec = Orientation.VEC[dirs[2].ordinal()];
        IntArrayList xs = new IntArrayList();
        IntArrayList ys = new IntArrayList();
        IntArrayList zs = new IntArrayList();
        List<TraceabilityPredicate> cells = new ArrayList<>();
        this.aisleEnd = new int[aisleCount];
        int controllerCell = -1;
        for (int c = 0; c < aisleCount; c++) {
            var aisle = grid[c];
            if (aisle != null) {
                for (int b = 0; b < height; b++) {
                    var row = aisle[b];
                    if (row == null) continue;
                    for (int a = 0; a < width; a++) {
                        var predicate = row[a];
                        if (predicate == null) continue;
                        if (predicate instanceof ControllerPredicate) {
                            if (controllerCell >= 0) throw new IllegalStateException("piece has more than one controller");
                            controllerCell = cells.size();
                        }
                        xs.add(a * charVec[0] + b * stringVec[0] + c * aisleVec[0]);
                        ys.add(a * charVec[1] + b * stringVec[1] + c * aisleVec[1]);
                        zs.add(a * charVec[2] + b * stringVec[2] + c * aisleVec[2]);
                        cells.add(predicate);
                    }
                }
            }
            aisleEnd[c] = cells.size();
        }
        this.size = cells.size();
        int[] bounds = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        for (int i = 0; i < size; i++) {
            bounds[0] = Math.min(bounds[0], xs.getInt(i));
            bounds[1] = Math.min(bounds[1], ys.getInt(i));
            bounds[2] = Math.min(bounds[2], zs.getInt(i));
            bounds[3] = Math.max(bounds[3], xs.getInt(i));
            bounds[4] = Math.max(bounds[4], ys.getInt(i));
            bounds[5] = Math.max(bounds[5], zs.getInt(i));
        }
        this.bounds = bounds;
        this.controller = controllerCell;
        var paletteIndex = new Reference2IntLinkedOpenHashMap<TraceabilityPredicate>();
        for (var predicate : cells) {
            if (!paletteIndex.containsKey(predicate)) paletteIndex.put(predicate, paletteIndex.size());
        }
        this.palette = paletteIndex.keySet().toArray(TraceabilityPredicate[]::new);
        if (palette.length <= 256) {
            this.smallIndex = new byte[size];
            this.largeIndex = null;
            for (int i = 0; i < size; i++) smallIndex[i] = (byte) paletteIndex.getInt(cells.get(i));
        } else {
            if (palette.length > 65536) throw new IllegalStateException("piece has too many distinct predicates: " + palette.length);
            this.smallIndex = null;
            this.largeIndex = new short[size];
            for (int i = 0; i < size; i++) largeIndex[i] = (short) paletteIndex.getInt(cells.get(i));
        }
        int bitsY = size == 0 ? 1 : bitsFor(bounds[4] - bounds[1] + 1);
        int bitsZ = size == 0 ? 1 : bitsFor(bounds[5] - bounds[2] + 1);
        int bitsX = size == 0 ? 1 : bitsFor(bounds[3] - bounds[0] + 1);
        if (bitsX + bitsY + bitsZ <= 32) {
            this.shiftY = bitsZ;
            this.shiftX = bitsY + bitsZ;
            this.maskY = (1 << bitsY) - 1;
            this.maskZ = (1 << bitsZ) - 1;
            this.packed = new int[size];
            for (int i = 0; i < size; i++) {
                packed[i] = (xs.getInt(i) - bounds[0]) << shiftX | (ys.getInt(i) - bounds[1]) << shiftY | (zs.getInt(i) - bounds[2]);
            }
            this.x = null;
            this.y = null;
            this.z = null;
        } else {
            this.shiftX = 0;
            this.shiftY = 0;
            this.maskY = 0;
            this.maskZ = 0;
            this.packed = null;
            this.x = xs.toIntArray();
            this.y = ys.toIntArray();
            this.z = zs.toIntArray();
        }
    }

    private static int bitsFor(int size) {
        return size <= 1 ? 1 : 32 - Integer.numberOfLeadingZeros(size - 1);
    }

    int size() {
        return size;
    }

    TraceabilityPredicate predicate(int cell) {
        return palette[smallIndex != null ? smallIndex[cell] & 0xFF : largeIndex[cell] & 0xFFFF];
    }

    int x(int cell) {
        return packed != null ? (packed[cell] >>> shiftX) + bounds[0] : x[cell];
    }

    int y(int cell) {
        return packed != null ? ((packed[cell] >>> shiftY) & maskY) + bounds[1] : y[cell];
    }

    int z(int cell) {
        return packed != null ? (packed[cell] & maskZ) + bounds[2] : z[cell];
    }

    Piece.Port port(PortKey key) {
        var port = template.ports.get(key);
        if (port == null) throw new IllegalArgumentException("piece has no port " + key);
        return port;
    }
}
