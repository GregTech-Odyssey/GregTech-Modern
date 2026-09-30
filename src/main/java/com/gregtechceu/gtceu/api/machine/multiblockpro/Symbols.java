package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;

import it.unimi.dsi.fastutil.chars.Char2ObjectLinkedOpenHashMap;

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
        return where(symbol, base.or(FactoryBlockPattern.utilityExcluding(Piece.utilityAbilities(), exclusions)));
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

    void applyTo(FactoryBlockPattern pattern) {
        for (var it = predicates.char2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            pattern.where(entry.getCharKey(), entry.getValue());
        }
    }
}
