package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;

import net.minecraft.world.level.block.Block;

import it.unimi.dsi.fastutil.chars.Char2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Symbols {

    private final Char2ObjectLinkedOpenHashMap<TraceabilityPredicate> predicates;

    private Symbols(Char2ObjectLinkedOpenHashMap<TraceabilityPredicate> predicates) {
        this.predicates = predicates;
    }

    public static Symbols create() {
        return new Symbols(new Char2ObjectLinkedOpenHashMap<>());
    }

    public Symbols where(char symbol, TraceabilityPredicate predicate) {
        predicates.put(symbol, predicate);
        return this;
    }

    public Symbols wherePart(char symbol, TraceabilityPredicate base) {
        return where(symbol, base.or(Piece.utilityAbilities()));
    }

    public Symbols wherePart(char symbol, TraceabilityPredicate base, TraceabilityPredicate exclusions) {
        return where(symbol, base.or(utilityExcluding(Piece.utilityAbilities(), exclusions)));
    }

    Symbols copy() {
        return new Symbols(predicates.clone());
    }

    boolean has(char symbol) {
        return predicates.containsKey(symbol);
    }

    TraceabilityPredicate get(char symbol) {
        var predicate = predicates.get(symbol);
        if (predicate == null) throw new IllegalArgumentException("symbol '" + symbol + "' is not defined");
        return predicate;
    }

    Char2ObjectOpenHashMap<TraceabilityPredicate> cells() {
        var cells = new Char2ObjectOpenHashMap<TraceabilityPredicate>(predicates.size());
        for (var it = predicates.char2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            var predicate = entry.getValue();
            if (predicate.isAny()) continue;
            cells.put(entry.getCharKey(), predicate.isAir() ? TraceabilityPredicate.AIR : predicate.sort());
        }
        return cells;
    }

    private static TraceabilityPredicate utilityExcluding(TraceabilityPredicate utilityAbilities, TraceabilityPredicate exclusions) {
        var excludedBlocks = candidateBlocks(exclusions, "wherePart exclusions");
        var allowedUtilities = new TraceabilityPredicate();
        for (SimplePredicate predicate : utilityAbilities.common) {
            var candidates = candidateBlocks(predicate, "utility ability");
            var allowed = candidates.stream()
                    .filter(block -> !excludedBlocks.contains(block))
                    .toArray(Block[]::new);
            if (allowed.length > 0) {
                allowedUtilities = allowedUtilities.or(Predicates.blocks(allowed).setPreviewCount(0));
            }
        }
        for (SimplePredicate predicate : utilityAbilities.limited) {
            var candidates = candidateBlocks(predicate, "utility ability");
            if (Collections.disjoint(candidates, excludedBlocks)) {
                allowedUtilities.limited.add(predicate);
            } else if (!excludedBlocks.containsAll(candidates)) {
                throw new IllegalArgumentException(
                        "wherePart exclusions cannot partially exclude a globally limited utility ability");
            }
        }
        return allowedUtilities;
    }

    private static ReferenceOpenHashSet<Block> candidateBlocks(TraceabilityPredicate predicate, String description) {
        var blocks = new ReferenceOpenHashSet<Block>();
        for (SimplePredicate simplePredicate : predicate.common) {
            blocks.addAll(candidateBlocks(simplePredicate, description));
        }
        for (SimplePredicate simplePredicate : predicate.limited) {
            blocks.addAll(candidateBlocks(simplePredicate, description));
        }
        if (blocks.isEmpty()) {
            throw new IllegalArgumentException(description + " must provide at least one block candidate");
        }
        return blocks;
    }

    private static List<Block> candidateBlocks(SimplePredicate predicate, String description) {
        if (predicate.candidates == null) {
            throw new IllegalArgumentException(description + " must provide enumerable block candidates");
        }
        var candidates = predicate.candidates.get();
        if (candidates == null) {
            throw new IllegalArgumentException(description + " returned null block candidates");
        }
        return Arrays.asList(candidates);
    }
}
