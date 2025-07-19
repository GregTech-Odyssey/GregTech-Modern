package com.gregtechceu.gtceu.api.pattern.util;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.function.Supplier;

/**
 * Contains an context used for storing temporary data
 * related to current check and shared between all predicates doing it
 */
public class PatternMatchContext {

    private final Object2ObjectOpenHashMap<Object, Object> data = new Object2ObjectOpenHashMap<>();

    public void reset() {
        this.data.clear();
    }

    public void set(Object key, Object value) {
        this.data.put(key, value);
    }

    public int getInt(Object key) {
        var value = data.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    public void increment(Object key, int value) {
        set(key, getOrDefault(key, 0) + value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(Object key, T defaultValue) {
        return (T) data.getOrDefault(key, defaultValue);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Object key) {
        return (T) data.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrCreate(Object key, Supplier<T> creator) {
        return (T) data.computeIfAbsent(key, k -> creator.get());
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrPut(Object key, T initialValue) {
        return (T) data.computeIfAbsent(key, k -> initialValue);
    }

    public boolean containsKey(Object key) {
        return data.containsKey(key);
    }

    public Object2ObjectMap.FastEntrySet<Object, Object> entrySet() {
        return data.object2ObjectEntrySet();
    }
}
