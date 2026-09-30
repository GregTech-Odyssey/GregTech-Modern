package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

final class CompiledStructure {

    final Structure definition;
    final CompiledPiece root;
    final CompiledPiece[] pieces;
    final Collection<TraceabilityPredicate> predicates;
    final SimplePredicate[][] limitPredicates;
    final int[] limitMax;
    @Nullable
    final Reference2ObjectOpenHashMap<CompiledPiece, int[]> limitMasks;

    CompiledStructure(Structure definition, Size size) {
        this.definition = definition;
        var compiled = new Reference2ObjectLinkedOpenHashMap<Piece, CompiledPiece>();
        this.root = compile(definition.root, size, definition.symbols, compiled);
        if (root.controller < 0) throw new IllegalStateException("root piece must contain the controller");
        if (definition.root.isSized()) definition.validateRootPorts(root.template);
        var slots = definition.slots;
        this.pieces = new CompiledPiece[slots.length];
        for (int node = 1; node < slots.length; node++) {
            if (!(slots[node] instanceof Slot.PieceSlot slot)) continue;
            var piece = compile(slot.piece, size, definition.symbols, compiled);
            pieces[node] = piece;
            if (slot.piece.isSized()) slot.validatePorts(piece.template);
        }
        var all = new ReferenceLinkedOpenHashSet<TraceabilityPredicate>();
        for (var piece : compiled.values()) {
            all.addAll(Arrays.asList(piece.palette));
        }
        this.predicates = all;
        int limitCount = definition.limitCount();
        this.limitPredicates = new SimplePredicate[limitCount][];
        this.limitMax = new int[limitCount];
        for (int i = 0; i < limitCount; i++) {
            limitPredicates[i] = definition.limitPredicates(i);
            limitMax[i] = definition.limitMax(i, size);
        }
        this.limitMasks = limitCount == 0 ? null : masks(compiled.values(), limitPredicates);
    }

    private static CompiledPiece compile(Piece piece, Size size, Symbols symbols, Reference2ObjectLinkedOpenHashMap<Piece, CompiledPiece> compiled) {
        var concrete = compiled.get(piece);
        if (concrete == null) {
            concrete = piece.resolve(size).compile(symbols);
            compiled.put(piece, concrete);
        }
        return concrete;
    }

    private static Reference2ObjectOpenHashMap<CompiledPiece, int[]> masks(Collection<CompiledPiece> pieces, SimplePredicate[][] limitPredicates) {
        var masks = new Reference2ObjectOpenHashMap<CompiledPiece, int[]>();
        for (var piece : pieces) {
            var mask = new int[piece.size()];
            boolean any = false;
            for (int cell = 0; cell < mask.length; cell++) {
                var predicate = piece.predicate(cell);
                for (int i = 0; i < limitPredicates.length; i++) {
                    for (var simple : limitPredicates[i]) {
                        if (predicate.common.contains(simple) || predicate.limited.contains(simple)) {
                            mask[cell] |= 1 << i;
                            any = true;
                        }
                    }
                }
            }
            if (any) masks.put(piece, mask);
        }
        return masks;
    }
}
