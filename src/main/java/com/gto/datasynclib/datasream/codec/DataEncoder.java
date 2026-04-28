package com.gto.datasynclib.datasream.codec;

import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.ListData;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public interface DataEncoder<T> {

    Data encode(T obj);

    static <K, V> DataEncoder<V> convert(DataEncoder<K> serializer, Function<V, K> converter) {
        return obj -> serializer.encode(converter.apply(obj));
    }

    static <K, V> DataEncoder<Map<K, V>> map(DataEncoder<K> keySerializer, DataEncoder<V> valueSerializer) {
        return map -> {
            var data = new ListData();
            map.forEach((k, v) -> {
                data.add(keySerializer.encode(k));
                data.add(valueSerializer.encode(v));
            });
            return data;
        };
    }

    static <E> DataEncoder<Set<E>> set(DataEncoder<E> serializer) {
        return list -> {
            var data = new ListData();
            list.forEach(o -> data.add(serializer.encode(o)));
            return data;
        };
    }

    static <E> DataEncoder<List<E>> list(DataEncoder<E> serializer) {
        return list -> {
            var data = new ListData();
            list.forEach(o -> data.add(serializer.encode(o)));
            return data;
        };
    }

    static <E> DataEncoder<Collection<E>> collection(DataEncoder<E> serializer) {
        return list -> {
            var data = new ListData();
            list.forEach(o -> data.add(serializer.encode(o)));
            return data;
        };
    }

    static <E> DataEncoder<E[]> array(DataEncoder<E> serializer) {
        return list -> {
            var data = new ListData();
            for (var o : list) {
                data.add(serializer.encode(o));
            }
            return data;
        };
    }
}
