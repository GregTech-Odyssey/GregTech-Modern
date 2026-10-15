package com.gregtechceu.gtceu.utils.cache;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.function.Supplier;

import static it.unimi.dsi.fastutil.HashCommon.arraySize;

public final class ClassInstanceCache<T> extends Reference2ReferenceOpenHashMap<Class<?>, Object> {

    private final Supplier<Iterable<T>> allSupplier;

    public ClassInstanceCache(Supplier<Iterable<T>> allSupplier) {
        this.allSupplier = allSupplier;
    }

    public ClassInstanceCache() {
        this.allSupplier = null;
    }

    public <C> C[] getInstance(Class<C> type) {
        final Object[] key = this.key;
        final var mask = this.mask;
        Object curr;
        int pos;
        if ((curr = key[pos = HashCommon.mix(System.identityHashCode(type)) & mask]) != null) {
            do if (curr == type) return (C[]) value[pos];
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        var arr = getTypeInstance(type, allSupplier.get());
        key[pos] = type;
        value[pos] = arr;
        if (size++ >= maxFill) rehash(arraySize(size + 1, f));
        return (C[]) arr;
    }

    public <C> C[] getInstance(Class<C> type, Iterable<T> all) {
        final Object[] key = this.key;
        final var mask = this.mask;
        Object curr;
        int pos;
        if ((curr = key[pos = HashCommon.mix(System.identityHashCode(type)) & mask]) != null) {
            do if (curr == type) return (C[]) value[pos];
            while ((curr = key[pos = (pos + 1) & mask]) != null);
        }
        var arr = getTypeInstance(type, all);
        key[pos] = type;
        value[pos] = arr;
        if (size++ >= maxFill) rehash(arraySize(size + 1, f));
        return (C[]) arr;
    }

    private Object[] getTypeInstance(Class<?> type, Iterable<T> all) {
        var arr = (Object[]) Array.newInstance(type, 0);
        var list = new ArrayList<>();
        for (var part : all) {
            if (type.isInstance(part)) {
                list.add(part);
            }
        }
        if (!list.isEmpty()) {
            arr = list.toArray(arr);
        }
        return arr;
    }
}
