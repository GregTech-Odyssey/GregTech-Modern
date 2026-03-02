package com.gregtechceu.gtceu.api.codec;

import com.gregtechceu.gtceu.api.codec.stream.ByteDataStream;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public interface ByteStreamEncoder<T> {

    void encode(@NotNull T obj, ByteDataStream stream);

    default byte @NotNull [] encodeToBytes(@NotNull T obj) {
        try (var baos = new ByteArrayOutputStream(); var dos = new DataOutputStream(baos)) {
            encode(obj, ByteDataStream.of(dos));
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    default void encodeToBuf(@NotNull T obj, ByteBuf buf) {
        encode(obj, ByteDataStream.of(buf));
    }

    static <K, V> ByteStreamEncoder<V> convert(ByteStreamEncoder<K> serializer, Function<V, K> converter) {
        return (obj, dos) -> serializer.encode(converter.apply(obj), dos);
    }

    static <K, V> ByteStreamEncoder<Map<K, V>> map(ByteStreamEncoder<K> keySerializer, ByteStreamEncoder<V> valueSerializer) {
        return (map, dos) -> {
            dos.writeVarInt(map.size());
            map.forEach((k, v) -> {
                keySerializer.encode(k, dos);
                valueSerializer.encode(v, dos);
            });
        };
    }

    static <E> ByteStreamEncoder<Set<E>> set(ByteStreamEncoder<E> serializer) {
        return (list, dos) -> {
            dos.writeVarInt(list.size());
            list.forEach(o -> serializer.encode(o, dos));
        };
    }

    static <E> ByteStreamEncoder<List<E>> list(ByteStreamEncoder<E> serializer) {
        return (list, dos) -> {
            dos.writeVarInt(list.size());
            list.forEach(o -> serializer.encode(o, dos));
        };
    }

    static <E> ByteStreamEncoder<Collection<E>> collection(ByteStreamEncoder<E> serializer) {
        return (list, dos) -> {
            dos.writeVarInt(list.size());
            list.forEach(o -> serializer.encode(o, dos));
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
