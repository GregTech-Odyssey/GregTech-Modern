package com.gregtechceu.gtceu.api.codec;

import com.gregtechceu.gtceu.api.codec.stream.ByteDataStream;

import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public interface ByteStreamDecoder<T> {

    T decode(ByteDataStream stream);

    default T decodeFromBytes(byte[] bytes) {
        try (var dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return decode(ByteDataStream.of(dis));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    default T decodeFromBuf(ByteBuf buf) {
        return decode(ByteDataStream.of(buf));
    }

    static <K, V> ByteStreamDecoder<V> convert(ByteStreamDecoder<K> serializer, Function<K, V> converter) {
        return dis -> converter.apply(serializer.decode(dis));
    }

    static <K, V> ByteStreamDecoder<Reference2ReferenceOpenHashMap<K, V>> map(ByteStreamDecoder<K> keySerializer, ByteStreamDecoder<V> valueSerializer) {
        return dis -> {
            int size = dis.readVarInt();
            Reference2ReferenceOpenHashMap<K, V> map = new Reference2ReferenceOpenHashMap<>(size);
            for (int i = 0; i < size; i++) {
                map.put(keySerializer.decode(dis), valueSerializer.decode(dis));
            }
            return map;
        };
    }

    static <K, V, M extends Map<K, V>> ByteStreamDecoder<M> map(Int2ObjectFunction<M> function, ByteStreamDecoder<K> keySerializer, ByteStreamDecoder<V> valueSerializer) {
        return dis -> {
            int size = dis.readVarInt();
            var map = function.apply(size);
            for (int i = 0; i < size; i++) {
                map.put(keySerializer.decode(dis), valueSerializer.decode(dis));
            }
            return map;
        };
    }

    static <E> ByteStreamDecoder<List<E>> list(ByteStreamDecoder<E> serializer) {
        return dis -> {
            int size = dis.readVarInt();
            var array = new Object[size];
            for (int i = 0; i < size; i++) array[i] = serializer.decode(dis);
            return (List) Arrays.asList(array);
        };
    }

    static <E, L extends List<E>> ByteStreamDecoder<L> list(Int2ObjectFunction<L> function, ByteStreamDecoder<E> serializer) {
        return dis -> {
            int size = dis.readVarInt();
            var list = function.apply(size);
            for (int i = 0; i < size; i++) list.add(serializer.decode(dis));
            return list;
        };
    }

    static <E> ByteStreamDecoder<ReferenceOpenHashSet<E>> set(ByteStreamDecoder<E> serializer) {
        return dis -> {
            int size = dis.readVarInt();
            var set = new ReferenceOpenHashSet<E>(size);
            for (int i = 0; i < size; i++) set.add(serializer.decode(dis));
            return set;
        };
    }

    static <E, S extends Set<E>> ByteStreamDecoder<S> set(Int2ObjectFunction<S> function, ByteStreamDecoder<E> serializer) {
        return dis -> {
            int size = dis.readVarInt();
            var set = function.apply(size);
            for (int i = 0; i < size; i++) set.add(serializer.decode(dis));
            return set;
        };
    }
}
