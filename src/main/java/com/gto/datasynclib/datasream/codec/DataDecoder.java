package com.gto.datasynclib.datasream.codec;

import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.ListData;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;

@FunctionalInterface
public interface DataDecoder<T> {

    T decode(Data data);

    static <K, V> DataDecoder<V> convert(DataDecoder<K> serializer, Function<K, V> converter) {
        return dis -> converter.apply(serializer.decode(dis));
    }

    static <K, V> DataDecoder<Reference2ReferenceOpenHashMap<K, V>> map(DataDecoder<K> keySerializer, DataDecoder<V> valueSerializer) {
        return dis -> {
            if (dis instanceof ListData(List<Data> list) && list.size() > 1) {
                Reference2ReferenceOpenHashMap<K, V> map = new Reference2ReferenceOpenHashMap<>(list.size() / 2);
                for (int i = 0; i < list.size(); i++) {
                    map.put(keySerializer.decode(list.get(i++)), valueSerializer.decode(list.get(i)));
                }
                return map;
            }
            return new Reference2ReferenceOpenHashMap<>(1);
        };
    }

    static <K, V, M extends Map<K, V>> DataDecoder<M> map(IntFunction<M> function, DataDecoder<K> keySerializer, DataDecoder<V> valueSerializer) {
        return dis -> {
            if (dis instanceof ListData(List<Data> list) && list.size() > 1) {
                var map = function.apply(list.size() / 2);
                for (int i = 0; i < list.size(); i++) {
                    map.put(keySerializer.decode(list.get(i++)), valueSerializer.decode(list.get(i)));
                }
                return map;
            }
            return function.apply(1);
        };
    }

    static <E> DataDecoder<List<E>> list(DataDecoder<E> serializer) {
        return dis -> {
            if (dis instanceof ListData(List<Data> list) && !list.isEmpty()) {
                var array = new Object[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    array[i] = serializer.decode(list.get(i));
                }
                return (List) Arrays.asList(array);
            }
            return Collections.emptyList();
        };
    }

    static <E, L extends List<E>> DataDecoder<L> list(IntFunction<L> function, DataDecoder<E> serializer) {
        return dis -> {
            if (dis instanceof ListData(List<Data> list) && !list.isEmpty()) {
                var array = function.apply(list.size());
                list.forEach(data -> array.add(serializer.decode(data)));
                return array;
            }
            return function.apply(1);
        };
    }

    static <E> DataDecoder<ReferenceOpenHashSet<E>> set(DataDecoder<E> serializer) {
        return dis -> {
            if (dis instanceof ListData(List<Data> list) && !list.isEmpty()) {
                var array = new ReferenceOpenHashSet<E>();
                list.forEach(data -> array.add(serializer.decode(data)));
                return array;
            }
            return new ReferenceOpenHashSet<>(1);
        };
    }

    static <E, S extends Set<E>> DataDecoder<S> set(IntFunction<S> function, DataDecoder<E> serializer) {
        return dis -> {
            if (dis instanceof ListData(List<Data> list) && !list.isEmpty()) {
                var array = function.apply(list.size());
                list.forEach(data -> array.add(serializer.decode(data)));
                return array;
            }
            return function.apply(1);
        };
    }
}
