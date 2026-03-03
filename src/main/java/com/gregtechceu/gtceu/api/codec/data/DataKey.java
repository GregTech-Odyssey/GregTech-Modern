package com.gregtechceu.gtceu.api.codec.data;

import com.gregtechceu.gtceu.api.codec.ByteStreamCodec;

import com.fast.fastcollection.O2OOpenCacheHashMap;
import it.unimi.dsi.fastutil.HashCommon;
import org.jetbrains.annotations.NotNull;

public class DataKey<T> {

    protected static final O2OOpenCacheHashMap<String, DataKey<?>> REGISTERED = new O2OOpenCacheHashMap<>();

    private static final DataKey<?> NULL = new DataKey<>("", null) {

        @Override
        public boolean isInvalid() {
            return true;
        }
    };

    public final String name;
    public final ByteStreamCodec<T> codec;
    public final int mixCode = HashCommon.mix(System.identityHashCode(this));

    protected DataKey(String name, ByteStreamCodec<T> codec) {
        this.name = name;
        this.codec = codec;
        register(this);
    }

    public boolean isInvalid() {
        return false;
    }

    @NotNull
    public static <T> DataKey<T> get(String name) {
        return (DataKey<T>) REGISTERED.getOrDefault(name, NULL);
    }

    public static <T> DataKey<T> register(String name, ByteStreamCodec<T> codec) {
        return new DataKey<>(name, codec);
    }

    public static synchronized <T> void register(DataKey<T> key) {
        REGISTERED.put(key.name, key);
    }
}
