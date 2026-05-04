package com.gto.datasynclib.util;

import com.gto.datasynclib.datasream.codec.ByteStreamCodec;
import com.gto.datasynclib.datasream.codec.DataCodec;
import com.gto.datasynclib.util.holder.IntObjectHolder;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Registry<K extends Comparable<K>, V> implements Iterable<V> {

    @Getter
    protected final String name;
    protected final HashMap<K, V> keyValues = new HashMap<>();
    protected final Reference2ReferenceOpenHashMap<V, IntObjectHolder<K>> valueKeys = new Reference2ReferenceOpenHashMap<>();
    protected final ReferenceArrayList<V> idValues = new ReferenceArrayList<>();
    @Getter
    protected volatile boolean frozen = true;

    public Registry(String name) {
        this.name = name;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean checkContext() {
        return true;
    }

    public void unfreeze() {
        if (!frozen) throw new IllegalStateException("Registry %s is already unfrozen!".formatted(name));
        if (!checkContext()) return;
        clear();
        this.frozen = false;
    }

    public void freeze() {
        if (frozen) throw new IllegalStateException("Registry %s is already frozen!".formatted(name));
        if (!checkContext()) return;
        frozen = true;
        build();
    }

    protected void clear() {
        keyValues.clear();
        valueKeys.clear();
        idValues.clear();
    }

    protected void build() {
        var list = new ArrayList<Map.Entry<K, V>>(keyValues.size());
        list.addAll(keyValues.entrySet());
        list.sort(Map.Entry.comparingByKey());
        var size = list.size();
        idValues.size(size);
        for (int i = 0; i < size; i++) {
            var e = list.get(i);
            var v = e.getValue();
            valueKeys.put(v, new IntObjectHolder<>(i, e.getKey()));
            idValues.set(i, v);
        }
    }

    public <T extends V> T register(K key, T value) {
        if (frozen) throw new IllegalStateException("Registry %s has been frozen".formatted(name));
        synchronized (this) {
            if (keyValues.put(key, value) != null) throw new IllegalStateException("Registry %s contains key %s already".formatted(name, key));
        }
        return value;
    }

    public <T extends V> T replace(K key, T value) {
        if (frozen) throw new IllegalStateException("Registry %s has been frozen".formatted(name));
        synchronized (this) {
            if (keyValues.put(key, value) == null) {
                throw new IllegalStateException("Couldn't find key %s in registry %s".formatted(name, key));
            }
        }
        return value;
    }

    public final boolean containKey(K key) {
        return keyValues.containsKey(key);
    }

    public final boolean containValue(V value) {
        return valueKeys.containsKey(value);
    }

    public final V get(int id) {
        return idValues.get(id);
    }

    @Nullable
    public final V get(K key) {
        return keyValues.get(key);
    }

    public final V getOrDefault(K key, V defaultValue) {
        return keyValues.getOrDefault(key, defaultValue);
    }

    public final int getId(V value) {
        return valueKeys.get(value).number;
    }

    public final K getKey(V value) {
        return valueKeys.get(value).obj;
    }

    public Set<V> values() {
        return valueKeys.keySet();
    }

    public Set<K> keys() {
        return keyValues.keySet();
    }

    @Override
    public @NotNull Iterator<V> iterator() {
        return valueKeys.keySet().iterator();
    }

    @Override
    public void forEach(Consumer<? super V> action) {
        valueKeys.keySet().forEach(action);
    }

    @Override
    public Spliterator<V> spliterator() {
        return valueKeys.keySet().spliterator();
    }

    public void forEachKey(Consumer<? super K> action) {
        keyValues.keySet().forEach(action);
    }

    public void forEachKeyValue(BiConsumer<? super K, ? super V> action) {
        keyValues.forEach(action);
    }

    public DataCodec<V> dataCodec(DataCodec<K> keyCodec) {
        return DataCodec.of(obj -> keyCodec.encode(valueKeys.get(obj).obj), data -> keyValues.get(keyCodec.decode(data)));
    }

    public ByteStreamCodec<V> streamCodec(ByteStreamCodec<K> keyCodec) {
        return ByteStreamCodec.of((buf, obj) -> keyCodec.encode(buf, keyCodec.decode(buf)), buf -> keyValues.get(keyCodec.decode(buf)));
    }

    public Codec<V> codec(Codec<K> keyCodec) {
        return keyCodec.xmap(keyValues::get, v -> valueKeys.get(v).obj);
    }
}
