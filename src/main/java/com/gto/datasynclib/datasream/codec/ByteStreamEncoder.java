package com.gto.datasynclib.datasream.codec;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Function;

@FunctionalInterface
public interface ByteStreamEncoder<T> {

    default void encode(T obj, FriendlyByteBuf buf) {
        encode(buf, obj);
    }

    void encode(FriendlyByteBuf buf, T obj);

    static <K, V> ByteStreamEncoder<V> convert(ByteStreamEncoder<K> serializer, Function<V, K> converter) {
        return (buf, obj) -> serializer.encode(buf, converter.apply(obj));
    }
}
