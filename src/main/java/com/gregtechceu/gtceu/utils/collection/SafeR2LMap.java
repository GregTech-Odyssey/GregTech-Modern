package com.gregtechceu.gtceu.utils.collection;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

public class SafeR2LMap<K> extends Reference2LongOpenHashMap<K> implements Iterable<Reference2LongMap.Entry<K>> {

    public SafeR2LMap() {
        super(DEFAULT_INITIAL_SIZE, DEFAULT_LOAD_FACTOR);
    }

    public SafeR2LMap(int size) {
        super(size, DEFAULT_LOAD_FACTOR);
    }

    @Override
    public @NotNull Iterator<Entry<K>> iterator() {
        return reference2LongEntrySet().fastIterator();
    }

    @Override
    public long addTo(final K k, final long incr) {
        if (k == null) return 0;
        int pos;
        K curr;
        final K[] key = this.key;
        if ((curr = key[pos = HashCommon.mix(System.identityHashCode(k)) & mask]) != null) {
            do if (curr == k) {
                final long oldValue = value[pos];
                final long newValue = oldValue + incr;
                if (newValue < 0 && incr >= 0 && oldValue >= 0) {
                    value[pos] = Long.MAX_VALUE;
                } else {
                    value[pos] = newValue;
                }
                return oldValue;
            }
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        key[pos] = k;
        value[pos] = incr;
        if (size++ >= maxFill) rehash(arraySize(size + 1, f));
        return 0;
    }

    public void ensureCapacity(int capacity) {
        int needed = (int) Math.min(1073741824L, Math.max(2L, HashCommon.nextPowerOfTwo((long) Math.ceil((float) (capacity + size) / this.f))));
        if (needed > this.n) {
            this.rehash(needed);
        }
    }

    public void reset() {
        for (int i = 0, len = value.length; i < len; i++) value[i] = 0;
    }
}
