package com.gregtechceu.gtceu.api.pattern.util;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;

import com.gto.datasynclib.datastream.DataComponentKey;
import com.gto.datasynclib.datastream.DataComponentMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Contains an context used for storing temporary data
 * related to current check and shared between all predicates doing it
 */
public class PatternMatchContext {

    private static final DataComponentKey<Long2ObjectOpenHashMap<TraceabilityPredicate>> PREDICATES = DataComponentKey.create("predicates", DataComponentKey.long2ObjectMapBuilder(Long2ObjectOpenHashMap::new));
    private static final DataComponentKey<ReferenceOpenHashSet<IMultiPart>> PARTS = DataComponentKey.create("parts", DataComponentKey.collectionBuilder(ReferenceOpenHashSet::new));

    private final DataComponentMap data = new DataComponentMap();

    public Long2ObjectOpenHashMap<TraceabilityPredicate> getPredicates() {
        return data.getOrCreateData(PREDICATES, Long2ObjectOpenHashMap::new);
    }

    public ReferenceSet<IMultiPart> getParts() {
        return data.getOrCreateData(PARTS, ReferenceOpenHashSet::new);
    }

    public void reset() {
        this.data.clear();
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
        return data.containsKey(key);
    }

    public void save(Checkpoint checkpoint, boolean values, DataComponentKey<?>[] untracked) {
        int size = data.size();
        if (checkpoint.keys.length <= size) checkpoint.keys = new Object[size + 4];
        var keys = checkpoint.keys;
        int i = 0;
        int count = 0;
        for (var it = data.reference2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            var key = entry.getKey();
            keys[i++] = key;
            if (!values || key == PREDICATES || key == PARTS || contains(untracked, key)) continue;
            checkpoint.put(count++, key, entry.getValue());
        }
        keys[i] = null;
        checkpoint.truncate(count);
    }

    public void restore(Checkpoint checkpoint) {
        for (int i = 0; i < checkpoint.valueCount; i++) {
            var value = checkpoint.values[i];
            Checkpoint.restoreContents(value, checkpoint.contents[i]);
            data.put(checkpoint.valueKeys[i], value);
        }
        var keys = checkpoint.keys;
        int count = 0;
        while (keys[count] != null) count++;
        if (data.size() == count) return;
        int size = count;
        data.keySet().removeIf(key -> {
            for (int i = 0; i < size; i++) {
                if (keys[i] == key) return false;
            }
            return true;
        });
    }

    private static boolean contains(DataComponentKey<?>[] keys, DataComponentKey<?> key) {
        for (var candidate : keys) {
            if (candidate == key) return true;
        }
        return false;
    }

    public static final class Checkpoint {

        private Object[] keys = new Object[8];
        private DataComponentKey<?>[] valueKeys = new DataComponentKey<?>[0];
        private Object[] values = new Object[0];
        private Object[] contents = new Object[0];
        private int valueCount;

        public void clear() {
            truncate(0);
        }

        private void put(int index, DataComponentKey<?> key, Object value) {
            if (index == values.length) {
                int capacity = Math.max(4, index * 2);
                valueKeys = Arrays.copyOf(valueKeys, capacity);
                values = Arrays.copyOf(values, capacity);
                contents = Arrays.copyOf(contents, capacity);
            }
            valueKeys[index] = key;
            values[index] = value;
            contents[index] = copyOf(value);
        }

        private void truncate(int count) {
            for (int i = count; i < valueCount; i++) {
                valueKeys[i] = null;
                values[i] = null;
                contents[i] = null;
            }
            valueCount = count;
        }

        @Nullable
        private static Object copyOf(Object value) {
            if (value instanceof LongCollection longs) return longs.toLongArray();
            if (value instanceof Reference2IntOpenHashMap<?> map) return map.clone();
            if (value instanceof Collection<?> collection) return collection.toArray();
            if (value instanceof Map<?, ?> map) return new Object2ObjectOpenHashMap<>(map);
            if (value instanceof int[] array) return array.clone();
            if (value instanceof long[] array) return array.clone();
            if (value instanceof double[] array) return array.clone();
            return null;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private static void restoreContents(Object value, @Nullable Object contents) {
            if (contents == null) return;
            if (value instanceof LongCollection longs) {
                var saved = (long[]) contents;
                if (longs.size() == saved.length) return;
                longs.clear();
                for (long element : saved) longs.add(element);
            } else if (value instanceof Reference2IntOpenHashMap map) {
                if (map.equals(contents)) return;
                map.clear();
                map.putAll((Reference2IntOpenHashMap) contents);
            } else if (value instanceof Collection collection) {
                var saved = (Object[]) contents;
                if (collection.size() == saved.length) return;
                collection.clear();
                Collections.addAll(collection, saved);
            } else if (value instanceof Map map) {
                if (map.equals(contents)) return;
                map.clear();
                map.putAll((Map) contents);
            } else if (value instanceof int[] array) {
                System.arraycopy((int[]) contents, 0, array, 0, array.length);
            } else if (value instanceof long[] array) {
                System.arraycopy((long[]) contents, 0, array, 0, array.length);
            } else if (value instanceof double[] array) {
                System.arraycopy((double[]) contents, 0, array, 0, array.length);
            }
        }
    }

    public ObjectSet<Reference2ObjectMap.Entry<DataComponentKey<?>, Object>> entrySet() {
        return data.reference2ObjectEntrySet();
    }
}
