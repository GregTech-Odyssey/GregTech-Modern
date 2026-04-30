package com.gto.datasynclib.datasream;

import net.minecraft.network.FriendlyByteBuf;

import com.fast.fastcollection.O2OOpenCacheHashMap;
import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.datasream.data.DataOps;
import com.gto.datasynclib.datasream.data.MapData;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Stream;

public final class DataComponentRegistry implements Codec<DataComponentMap> {

    private final O2OOpenCacheHashMap<String, DataComponentKey<?>> keys = new O2OOpenCacheHashMap<>();
    private final Reference2IntOpenHashMap<DataComponentKey<?>> ids = new Reference2IntOpenHashMap<>();
    private final Int2ObjectOpenHashMap<DataComponentKey<?>> idKeys = new Int2ObjectOpenHashMap<>();
    private boolean frozen;

    public <T> DataComponentKey<T> register(String key, CombinationCodec<T> codec) {
        return register(new DataComponentKey<>(key, codec));
    }

    public <T> DataComponentKey<T> register(DataComponentKey<T> key) {
        if (frozen) throw new IllegalStateException("Registry is frozen");
        synchronized (this) {
            if (keys.put(key.name, key) != null) throw new IllegalArgumentException("Duplicate key: " + key.name);
        }
        return key;
    }

    public <T> DataComponentKey<T> get(String name) {
        return (DataComponentKey<T>) keys.get(name);
    }

    public <T> DataComponentKey<T> get(int id) {
        return (DataComponentKey<T>) idKeys.get(id);
    }

    public int getId(DataComponentKey<?> key) {
        return ids.getInt(key);
    }

    public void freeze() {
        if (frozen) throw new IllegalStateException("Registry is already frozen!");
        frozen = true;
        var list = new ArrayList<DataComponentKey<?>>(keys.size());
        list.addAll(keys.values());
        list.sort(Comparator.comparing(k -> k.name));
        for (int i = 0; i < list.size(); i++) {
            var k = list.get(i);
            ids.put(k, i);
            idKeys.put(i, k);
        }
    }

    public void write(FriendlyByteBuf buf, DataComponentMap map) {
        buf.writeVarInt(map.size());
        map.fastForEach((k, v) -> {
            buf.writeVarInt(getId(k));
            k.codec.streamWriter.encode(buf, v);
        });
    }

    public DataComponentMap read(FriendlyByteBuf buf) {
        var size = buf.readVarInt();
        var map = new DataComponentMap(size);
        for (int i = 0; i < size; i++) {
            var key = get(buf.readVarInt());
            if (key == null) continue;
            var value = key.codec.streamReader.decode(buf);
            if (value != null) map.put(key, value);
        }
        return map;
    }

    public void write(DataComponentMap map, MapData data) {
        map.fastForEach((k, v) -> data.put(k.name, k.codec.dataWriter.encode(v)));
    }

    public DataComponentMap read(MapData data) {
        var map = new DataComponentMap(data.size());
        data.value().forEach((k, v) -> {
            var key = get(k);
            if (key == null) return;
            var value = key.codec.dataReader.decode(data);
            if (value != null) map.put(key, value);
        });
        return map;
    }

    @Override
    public <T> DataResult<Pair<DataComponentMap, T>> decode(DynamicOps<T> ops, T input) {
        var map = new DataComponentMap();
        ops.getMapValues(input).result().orElse(Stream.empty()).forEach(p -> {
            var key = get(ops.convertTo(DataOps.INSTANCE, p.getFirst()).getString());
            if (key == null) return;
            var value = key.codec.dataReader.decode(ops.convertTo(DataOps.INSTANCE, p.getSecond()));
            if (value != null) map.put(key, value);
        });
        return DataResult.success(Pair.of(map, ops.empty()));
    }

    @Override
    public <T> DataResult<T> encode(DataComponentMap input, DynamicOps<T> ops, T prefix) {
        var data = new MapData(new Reference2ReferenceOpenHashMap<>());
        input.fastForEach((k, v) -> data.put(k.name, k.codec.dataWriter.encode(v)));
        return DataResult.success(DataOps.INSTANCE.convertMap(ops, data));
    }
}
