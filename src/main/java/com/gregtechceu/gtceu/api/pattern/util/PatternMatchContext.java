package com.gregtechceu.gtceu.api.pattern.util;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;

import com.fast.fastcollection.O2OOpenCacheHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.function.Supplier;

/**
 * Contains an context used for storing temporary data
 * related to current check and shared between all predicates doing it
 */
public class PatternMatchContext {

    public final LongOpenHashSet vaBlocks = new LongOpenHashSet();
    public final ReferenceOpenHashSet<IMultiPart> parts = new ReferenceOpenHashSet<>();

    private final PatternMatchContext parents;

    private Long2ObjectOpenHashMap<TraceabilityPredicate> predicates;

    private O2OOpenCacheHashMap<Object, Object> data;

    public PatternMatchContext() {
        this.parents = null;
    }

    public PatternMatchContext(PatternMatchContext parents) {
        this.parents = parents;
        mergeData(parents);
    }

    private void mergeData(PatternMatchContext context) {
        if (context.data != null) {
            if (data == null) data = new O2OOpenCacheHashMap<>();
            data.putAll(context.data);
        }
    }

    public Long2ObjectOpenHashMap<TraceabilityPredicate> getPredicates() {
        if (predicates == null) predicates = new Long2ObjectOpenHashMap<>();
        return predicates;
    }

    public void merge(PatternMatchContext state) {
        if (state.predicates != null) {
            if (predicates == null) predicates = new Long2ObjectOpenHashMap<>();
            predicates.putAll(state.predicates);
        }
        vaBlocks.addAll(state.vaBlocks);
        parts.addAll(state.parts);
    }

    public void reset() {
        vaBlocks.clear();
        parts.clear();
        if (predicates != null) this.predicates.clear();
        if (data != null) this.data.clear();
        if (parents != null) mergeData(parents);
    }

    public void set(Object key, Object value) {
        if (data == null) data = new O2OOpenCacheHashMap<>();
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
        if (data == null) data = new O2OOpenCacheHashMap<>();
        return (T) data.computeIfAbsent(key, k -> creator.get());
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrPut(Object key, T initialValue) {
        if (data == null) data = new O2OOpenCacheHashMap<>();
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
