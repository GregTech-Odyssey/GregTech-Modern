package com.gto.datasynclib.datasream;

import com.gto.datasynclib.CombinationCodec;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMaps;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongMaps;

import java.util.Collection;
import java.util.Map;

public class DataComponentKey<T> {

    public static <T> DataComponentKey<T> createNoCodec(String name) {
        return new DataComponentKey<>(name, null);
    }

    public static <T> DataComponentKey<T> create(String name, CombinationCodec<T> codec) {
        return new DataComponentKey<>(name, codec);
    }

    public static <K, V, M extends Map<K, V>> DataComponentKey<M> createMap(String name, CombinationCodec<M> codec) {
        return (DataComponentKey) new MapKey(name, codec);
    }

    public static <T, C extends Collection<T>> DataComponentKey<C> createCollection(String name, CombinationCodec<C> codec) {
        return (DataComponentKey) new CollectionKey(name, codec);
    }

    public static <T extends IntSet> DataComponentKey<T> createIntSet(String name, CombinationCodec<T> codec) {
        return (DataComponentKey) new IntSetKey(name, codec);
    }

    public static <T extends LongSet> DataComponentKey<T> createLongSet(String name, CombinationCodec<T> codec) {
        return (DataComponentKey) new LongSetKey(name, codec);
    }

    public static <K, M extends Reference2IntMap<K>> DataComponentKey<M> createReference2IntMap(String name, CombinationCodec<M> codec) {
        return (DataComponentKey) new Reference2IntMapKey(name, codec);
    }

    public static <K, M extends Reference2LongMap<K>> DataComponentKey<M> createReference2LongMap(String name, CombinationCodec<M> codec) {
        return (DataComponentKey) new Reference2LongMapKey(name, codec);
    }

    public static <V, M extends Int2ObjectMap<V>> DataComponentKey<M> createInt2ObjectMap(String name, CombinationCodec<M> codec) {
        return (DataComponentKey) new Int2ObjectMapKey(name, codec);
    }

    public static <V, M extends Long2ObjectMap<V>> DataComponentKey<M> createLong2ObjectMap(String name, CombinationCodec<M> codec) {
        return (DataComponentKey) new Long2ObjectMapKey(name, codec);
    }

    public static <M extends Long2BooleanMap> DataComponentKey<M> createLong2BooleanMap(String name, CombinationCodec<M> codec) {
        return (DataComponentKey) new Long2BooleanMapKey(name, codec);
    }

    public final String name;
    public final CombinationCodec<T> codec;
    public final int mixCode = HashCommon.mix(System.identityHashCode(this));

    protected DataComponentKey(String name, CombinationCodec<T> codec) {
        this.name = name;
        this.codec = codec;
    }

    public final Object mergeData(Object oldData, Object newData) {
        return merge((T) oldData, (T) newData);
    }

    protected T merge(T oldData, T newData) {
        return newData;
    }

    @Override
    public String toString() {
        return name;
    }

    private static final class MapKey extends DataComponentKey<Map> {

        private MapKey(String name, CombinationCodec codec) {
            super(name, codec);
        }

        @Override
        public Map merge(Map oldData, Map newData) {
            oldData.putAll(newData);
            return oldData;
        }
    }

    private static final class CollectionKey extends DataComponentKey<Collection> {

        private CollectionKey(String name, CombinationCodec codec) {
            super(name, codec);
        }

        @Override
        public Collection merge(Collection oldData, Collection newData) {
            oldData.addAll(newData);
            return oldData;
        }
    }

    private static final class IntSetKey extends DataComponentKey<IntSet> {

        private IntSetKey(String name, CombinationCodec codec) {
            super(name, codec);
        }

        @Override
        public IntSet merge(IntSet oldData, IntSet newData) {
            oldData.addAll(newData);
            return oldData;
        }
    }

    private static final class LongSetKey extends DataComponentKey<LongSet> {

        private LongSetKey(String name, CombinationCodec codec) {
            super(name, codec);
        }

        @Override
        public LongSet merge(LongSet oldData, LongSet newData) {
            oldData.addAll(newData);
            return oldData;
        }
    }

    private static final class Reference2IntMapKey extends DataComponentKey<Reference2IntMap> {

        private Reference2IntMapKey(String name, CombinationCodec codec) {
            super(name, codec);
        }

        @Override
        public Reference2IntMap merge(Reference2IntMap oldData, Reference2IntMap newData) {
            Reference2IntMaps.fastForEach(newData, e -> oldData.put(e.getKey(), e.getIntValue()));
            return oldData;
        }
    }

    private static final class Reference2LongMapKey extends DataComponentKey<Reference2LongMap> {

        private Reference2LongMapKey(String name, CombinationCodec codec) {
            super(name, codec);
        }

        @Override
        public Reference2LongMap merge(Reference2LongMap oldData, Reference2LongMap newData) {
            Reference2LongMaps.fastForEach(newData, e -> oldData.put(e.getKey(), e.getLongValue()));
            return oldData;
        }
    }

    private static final class Int2ObjectMapKey extends DataComponentKey<Int2ObjectMap> {

        private Int2ObjectMapKey(String name, CombinationCodec codec) {
            super(name, codec);
        }

        @Override
        public Int2ObjectMap merge(Int2ObjectMap oldData, Int2ObjectMap newData) {
            Int2ObjectMaps.fastForEach(newData, e -> oldData.put(e.getIntKey(), e.getValue()));
            return oldData;
        }
    }

    private static final class Long2ObjectMapKey extends DataComponentKey<Long2ObjectMap> {

        private Long2ObjectMapKey(String name, CombinationCodec codec) {
            super(name, codec);
        }

        @Override
        public Long2ObjectMap merge(Long2ObjectMap oldData, Long2ObjectMap newData) {
            Long2ObjectMaps.fastForEach(newData, e -> oldData.put(e.getLongKey(), e.getValue()));
            return oldData;
        }
    }

    private static final class Long2BooleanMapKey extends DataComponentKey<Long2BooleanMap> {

        private Long2BooleanMapKey(String name, CombinationCodec codec) {
            super(name, codec);
        }

        @Override
        public Long2BooleanMap merge(Long2BooleanMap oldData, Long2BooleanMap newData) {
            Long2BooleanMaps.fastForEach(newData, e -> oldData.put(e.getLongKey(), e.getBooleanValue()));
            return oldData;
        }
    }
}
