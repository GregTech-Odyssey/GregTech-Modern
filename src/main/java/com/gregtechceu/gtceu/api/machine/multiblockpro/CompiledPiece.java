package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.ControllerPredicate;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.List;

final class CompiledPiece {

    final Piece template;
    final int aisleCount;
    final int[] x;
    final int[] y;
    final int[] z;
    final TraceabilityPredicate[] predicates;
    final int[] aisleEnd;
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
        this.x = xs.toIntArray();
        this.y = ys.toIntArray();
        this.z = zs.toIntArray();
        int[] bounds = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };
        for (int i = 0; i < x.length; i++) {
            bounds[0] = Math.min(bounds[0], x[i]);
            bounds[1] = Math.min(bounds[1], y[i]);
            bounds[2] = Math.min(bounds[2], z[i]);
            bounds[3] = Math.max(bounds[3], x[i]);
            bounds[4] = Math.max(bounds[4], y[i]);
            bounds[5] = Math.max(bounds[5], z[i]);
        }
        this.bounds = bounds;
        this.predicates = cells.toArray(TraceabilityPredicate[]::new);
        this.controller = controllerCell;
    }

    Piece.Port port(PortKey key) {
        var port = template.ports.get(key);
        if (port == null) throw new IllegalArgumentException("piece has no port " + key);
        return port;
    }
}
