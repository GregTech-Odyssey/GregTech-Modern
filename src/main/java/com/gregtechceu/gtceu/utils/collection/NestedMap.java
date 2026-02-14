package com.gregtechceu.gtceu.utils.collection;

import com.google.common.base.Supplier;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

public interface NestedMap<K1, K2, V> {

    static <K1, K2, V> NestedMap<K1, K2, V> create(Map<K1, Map<K2, V>> map, Supplier<Map<K2, V>> factory) {
        return new NestedMapWrapper<>(map, factory);
    }

    Map<K1, Map<K2, V>> getMap();

    Function<Object, Map<K2, V>> getFactory();

    default Map<K2, V> getMap(Object k1) {
        var innerMap = getMap().get(k1);
        return innerMap == null ? Collections.emptyMap() : innerMap;
    }

    default V get(Object k1, Object k2) {
        Map<K2, V> innerMap = getMap().get(k1);
        return innerMap == null ? null : innerMap.get(k2);
    }

    default V put(K1 k1, K2 k2, V value) {
        return getMap().computeIfAbsent(k1, getFactory()).put(k2, value);
    }

    default V computeIfAbsent(K1 k1, K2 k2, Function<? super K2, ? extends V> mappingFunction) {
        return getMap().computeIfAbsent(k1, getFactory()).computeIfAbsent(k2, mappingFunction);
    }

    default V remove(Object k1, Object k2) {
        var map = getMap();
        Map<K2, V> innerMap = map.get(k1);
        if (innerMap == null) return null;
        V removed = innerMap.remove(k2);
        if (innerMap.isEmpty()) map.remove(k1);
        return removed;
    }

    default boolean containsKey(Object k1, Object k2) {
        Map<K2, V> innerMap = getMap().get(k1);
        return innerMap != null && innerMap.containsKey(k2);
    }

    default boolean isEmpty() {
        return getMap().isEmpty();
    }

    default void clear() {
        getMap().clear();
    }

    class NestedMapWrapper<K1, K2, V> implements NestedMap<K1, K2, V> {

        @Getter
        private final Map<K1, Map<K2, V>> map;
        @Getter
        private final Function<Object, Map<K2, V>> factory;

        private NestedMapWrapper(Map<K1, Map<K2, V>> map, Supplier<Map<K2, V>> factory) {
            this.map = map;
            this.factory = a -> factory.get();
        }
    }
}
