package com.gregtechceu.gtceu.api.pattern.util;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.*;

import java.util.function.Supplier;

/**
 * Contains an context used for storing temporary data
 * related to current check and shared between all predicates doing it
 */
public class PatternMatchContext {

    public final LongOpenHashSet vaBlocks = new LongOpenHashSet();
    public final ObjectOpenHashSet<IMultiPart> parts = new ObjectOpenHashSet<>();

    private Long2ObjectOpenHashMap<TraceabilityPredicate> predicates;

    private Object2ObjectOpenHashMap<Object, Object> data = new Object2ObjectOpenHashMap<>();

    public Long2ObjectOpenHashMap<TraceabilityPredicate> getPredicates() {
        if (predicates == null) predicates = new Long2ObjectOpenHashMap<>();
        return predicates;
    }

    public void merge(PatternMatchContext state) {
        if (state.data != null) {
            if (data == null) data = new Object2ObjectOpenHashMap<>();
            data.putAll(state.data);
        }
        if (state.predicates != null) {
            if (predicates == null) predicates = new Long2ObjectOpenHashMap<>();
            predicates.putAll(state.predicates);
        }
        vaBlocks.addAll(state.vaBlocks);
        parts.addAll(state.parts);
    }

    public void reset() {
        if (data == null) return;
        this.data.clear();
    }

    public void set(Object key, Object value) {
        if (data == null) data = new Object2ObjectOpenHashMap<>();
        this.data.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(Object key, T defaultValue) {
        if (data == null) return defaultValue;
        return (T) data.getOrDefault(key, defaultValue);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Object key) {
        if (data == null) return null;
        return (T) data.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrCreate(Object key, Supplier<T> creator) {
        if (data == null) data = new Object2ObjectOpenHashMap<>();
        return (T) data.computeIfAbsent(key, k -> creator.get());
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrPut(Object key, T initialValue) {
        if (data == null) data = new Object2ObjectOpenHashMap<>();
        return (T) data.computeIfAbsent(key, k -> initialValue);
    }

    public boolean containsKey(Object key) {
        if (data == null) return false;
        return data.containsKey(key);
    }

    public ObjectSet<Object2ObjectMap.Entry<Object, Object>> entrySet() {
        if (data == null) return Object2ObjectMaps.emptyMap().object2ObjectEntrySet();
        return data.object2ObjectEntrySet();
    }
}
