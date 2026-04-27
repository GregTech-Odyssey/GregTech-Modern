package com.gregtechceu.gtceu.api.misc.data;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMaps;

import java.util.Collection;
import java.util.Map;

public class DataComponentKey<T> {

    public static <T> DataComponentKey<T> create(String name, ByteStreamCodec<T> codec) {
        return new DataComponentKey<>(name, codec);
    }

    public static <K, V, M extends Map<K, V>> DataComponentKey<M> createMap(String name, ByteStreamCodec<M> codec) {
        return (DataComponentKey) new MapKey(name, (ByteStreamCodec) codec);
    }

    public static <T, C extends Collection<T>> DataComponentKey<C> createCollection(String name, ByteStreamCodec<C> codec) {
        return (DataComponentKey) new CollectionKey(name, (ByteStreamCodec) codec);
    }

    public static <T extends IntSet> DataComponentKey<T> createIntSet(String name, ByteStreamCodec<T> codec) {
        return (DataComponentKey) new IntSetKey(name, (ByteStreamCodec) codec);
    }

    public static <T extends LongSet> DataComponentKey<T> createLongSet(String name, ByteStreamCodec<T> codec) {
        return (DataComponentKey) new LongSetKey(name, (ByteStreamCodec) codec);
    }

    public static <K, M extends Reference2IntMap<K>> DataComponentKey<M> createReference2IntMap(String name, ByteStreamCodec<M> codec) {
        return (DataComponentKey) new Reference2IntMapKey(name, (ByteStreamCodec) codec);
    }

    public static <V, M extends Long2ObjectMap<V>> DataComponentKey<M> createLong2ObjectMap(String name, ByteStreamCodec<M> codec) {
        return (DataComponentKey) new Long2ObjectMapKey(name, (ByteStreamCodec) codec);
    }

    public static <M extends Long2BooleanMap> DataComponentKey<M> createLong2BooleanMap(String name, ByteStreamCodec<M> codec) {
        return (DataComponentKey) new Long2BooleanMapKey(name, (ByteStreamCodec) codec);
    }

    public final String name;
    public final ByteStreamCodec<T> codec;
    public final int mixCode = HashCommon.mix(System.identityHashCode(this));

    protected DataComponentKey(String name, ByteStreamCodec<T> codec) {
        this.name = name;
        this.codec = codec;
    }

    final Object mergeData(Object oldData, Object newData) {
        return merge((T) oldData, (T) newData);
    }

    public T merge(T oldData, T newData) {
        return newData;
    }

    @Override
    public String toString() {
        return name;
    }

    private static final class MapKey extends DataComponentKey<Map> {

        private MapKey(String name, ByteStreamCodec<Map> codec) {
            super(name, codec);
        }

        @Override
        public Map merge(Map oldData, Map newData) {
            oldData.putAll(newData);
            return oldData;
        }
    }

    private static final class CollectionKey extends DataComponentKey<Collection> {

        private CollectionKey(String name, ByteStreamCodec<Collection> codec) {
            super(name, codec);
        }

        @Override
        public Collection merge(Collection oldData, Collection newData) {
            oldData.addAll(newData);
            return oldData;
        }
    }

    private static final class IntSetKey extends DataComponentKey<IntSet> {

        private IntSetKey(String name, ByteStreamCodec<IntSet> codec) {
            super(name, codec);
        }

        @Override
        public IntSet merge(IntSet oldData, IntSet newData) {
            oldData.addAll(newData);
            return oldData;
        }
    }

    private static final class LongSetKey extends DataComponentKey<LongSet> {

        private LongSetKey(String name, ByteStreamCodec<LongSet> codec) {
            super(name, codec);
        }

        @Override
        public LongSet merge(LongSet oldData, LongSet newData) {
            oldData.addAll(newData);
            return oldData;
        }
    }

    private static final class Reference2IntMapKey extends DataComponentKey<Reference2IntMap> {

        private Reference2IntMapKey(String name, ByteStreamCodec<Reference2IntMap> codec) {
            super(name, codec);
        }

        @Override
        public Reference2IntMap merge(Reference2IntMap oldData, Reference2IntMap newData) {
            Reference2IntMaps.fastForEach(newData, e -> oldData.put(e.getKey(), e.getIntValue()));
            return oldData;
        }
    }

    private static final class Long2ObjectMapKey extends DataComponentKey<Long2ObjectMap> {

        private Long2ObjectMapKey(String name, ByteStreamCodec<Long2ObjectMap> codec) {
            super(name, codec);
        }

        @Override
        public Long2ObjectMap merge(Long2ObjectMap oldData, Long2ObjectMap newData) {
            Long2ObjectMaps.fastForEach(newData, e -> oldData.put(e.getLongKey(), e.getValue()));
            return oldData;
        }
    }

    private static final class Long2BooleanMapKey extends DataComponentKey<Long2BooleanMap> {

        private Long2BooleanMapKey(String name, ByteStreamCodec<Long2BooleanMap> codec) {
            super(name, codec);
        }

        @Override
        public Long2BooleanMap merge(Long2BooleanMap oldData, Long2BooleanMap newData) {
            Long2BooleanMaps.fastForEach(newData, e -> oldData.put(e.getLongKey(), e.getBooleanValue()));
            return oldData;
        }
    }
}
