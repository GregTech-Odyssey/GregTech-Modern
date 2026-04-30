package com.gto.datasynclib.datasream.codec;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Function;

@FunctionalInterface
public interface ByteStreamDecoder<T> {

    T decode(FriendlyByteBuf buf);

    static <K, V> ByteStreamDecoder<V> convert(ByteStreamDecoder<K> serializer, Function<K, V> converter) {
        return dis -> converter.apply(serializer.decode(dis));
    }
}
