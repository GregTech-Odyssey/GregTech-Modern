package com.gto.datasynclib.datasream.codec;

import com.gto.datasynclib.datasream.stream.ByteDataStream;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public interface ByteStreamEncoder<T> {

    void encode(T obj, ByteDataStream stream) throws IOException;

    static <K, V> ByteStreamEncoder<V> convert(ByteStreamEncoder<K> serializer, Function<V, K> converter) {
        return (obj, dos) -> serializer.encode(converter.apply(obj), dos);
    }

    static <K, V> ByteStreamEncoder<Map<K, V>> map(ByteStreamEncoder<K> keySerializer, ByteStreamEncoder<V> valueSerializer) {
        return (map, dos) -> {
            dos.writeVarInt(map.size());
            map.forEach((k, v) -> {
                try {
                    keySerializer.encode(k, dos);
                    valueSerializer.encode(v, dos);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        };
    }

    static <E> ByteStreamEncoder<Set<E>> set(ByteStreamEncoder<E> serializer) {
        return (list, dos) -> {
            dos.writeVarInt(list.size());
            list.forEach(o -> {
                try {
                    serializer.encode(o, dos);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        };
    }

    static <E> ByteStreamEncoder<List<E>> list(ByteStreamEncoder<E> serializer) {
        return (list, dos) -> {
            dos.writeVarInt(list.size());
            list.forEach(o -> {
                try {
                    serializer.encode(o, dos);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        };
    }

    static <E> ByteStreamEncoder<Collection<E>> collection(ByteStreamEncoder<E> serializer) {
        return (list, dos) -> {
            dos.writeVarInt(list.size());
            list.forEach(o -> {
                try {
                    serializer.encode(o, dos);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        };
    }

    static <E> ByteStreamEncoder<E[]> array(ByteStreamEncoder<E> serializer) {
        return (list, dos) -> {
            dos.writeVarInt(list.length);
            for (E o : list) {
                serializer.encode(o, dos);
            }
        };
    }
}
