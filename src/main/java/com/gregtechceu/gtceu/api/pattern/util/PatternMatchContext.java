package com.gregtechceu.gtceu.api.pattern.util;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;

import com.gto.datasynclib.datasream.DataComponentKey;
import com.gto.datasynclib.datasream.DataComponentMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;

import java.util.function.Supplier;

/**
 * Contains an context used for storing temporary data
 * related to current check and shared between all predicates doing it
 */
public class PatternMatchContext {

    private static final DataComponentKey<Long2ObjectOpenHashMap<TraceabilityPredicate>> PREDICATES = DataComponentKey.createLong2ObjectMap("predicates", null);
    private static final DataComponentKey<ReferenceOpenHashSet<IMultiPart>> PARTS = DataComponentKey.createCollection("parts", null);

    private final PatternMatchContext parents;

    private final DataComponentMap data = new DataComponentMap();

    public PatternMatchContext() {
        this.parents = null;
    }

    public PatternMatchContext(PatternMatchContext parents) {
        this.parents = parents;
        this.data.merge(parents.data);
    }

    public Long2ObjectOpenHashMap<TraceabilityPredicate> getPredicates() {
        return data.getOrCreateData(PREDICATES, Long2ObjectOpenHashMap::new);
    }

    public ReferenceSet<IMultiPart> getParts() {
        return data.getOrCreateData(PARTS, ReferenceOpenHashSet::new);
    }

    public void merge(PatternMatchContext state) {
        data.merge(state.data);
    }

    public void reset() {
        this.data.clear();
        if (parents != null) this.data.merge(parents.data);
    }

    public <T> void set(DataComponentKey<T> key, T value) {
        this.data.put(key, value);
    }

    public <T> T getOrDefault(DataComponentKey<T> key, T defaultValue) {
        return data.getOrDefaultData(key, defaultValue);
    }

    public <T> T get(DataComponentKey<T> key) {
        return data.getData(key);
    }

    public <T> T getOrCreate(DataComponentKey<T> key, Supplier<T> creator) {
        return data.getOrCreateData(key, creator);
    }

    public <T> T getOrPut(DataComponentKey<T> key, T initialValue) {
        return data.getOrPut(key, initialValue);
    }

    public <T> boolean containsKey(DataComponentKey<T> key) {
        return data.contains(key);
    }

    public ObjectSet<Reference2ObjectMap.Entry<DataComponentKey<?>, Object>> entrySet() {
        return data.reference2ObjectEntrySet();
    }
}
