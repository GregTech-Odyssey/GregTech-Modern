package com.gto.datasynclib.datasream;

import com.fast.fastcollection.O2OOpenCacheHashMap;
import com.gto.datasynclib.datasream.codec.DataCodec;
import com.gto.datasynclib.datasream.data.DataOps;
import com.gto.datasynclib.datasream.data.MapData;
import com.gto.datasynclib.datasream.stream.ByteBufWrapper;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Stream;

public final class DataComponentRegistry implements Codec<DataComponentMap> {

    private final O2OOpenCacheHashMap<String, DataComponentKey<?>> keys = new O2OOpenCacheHashMap<>();
    private final Reference2IntOpenHashMap<DataComponentKey<?>> ids = new Reference2IntOpenHashMap<>();
    private final Int2ObjectOpenHashMap<DataComponentKey<?>> idKeys = new Int2ObjectOpenHashMap<>();
    private boolean frozen;

    public <T> DataComponentKey<T> register(String key, DataCodec<T> codec) {
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

    public void write(DataComponentMap map, ByteBuf buf) {
        var stream = new ByteBufWrapper(buf);
        try {
            stream.writeVarInt(map.size());
            map.fastForEach((k, v) -> {
                try {
                    stream.writeVarInt(getId(k));
                    stream.writeData(k.codec.encode(v));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public DataComponentMap read(ByteBuf buf) {
        var stream = new ByteBufWrapper(buf);
        try {
            var size = stream.readVarInt();
            var map = new DataComponentMap(size);
            for (int i = 0; i < size; i++) {
                var key = get(stream.readVarInt());
                var data = stream.readData();
                if (key == null) continue;
                var value = key.codec.decode(data);
                if (value != null) map.put(key, value);
            }
            return map;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void write(DataComponentMap map, ByteDataStream stream) throws IOException {
        stream.writeVarInt(map.size());
        map.fastForEach((k, v) -> {
            try {
                stream.writeUTF(k.name);
                stream.writeData(k.codec.encode(v));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public DataComponentMap read(ByteDataStream stream) throws IOException {
        var size = stream.readVarInt();
        var map = new DataComponentMap(size);
        for (int i = 0; i < size; i++) {
            var key = get(stream.readUTF());
            var data = stream.readData();
            if (key == null) continue;
            var value = key.codec.decode(data);
            if (value != null) map.put(key, value);
        }
        return map;
    }

    public void write(DataComponentMap map, MapData data) {
        map.fastForEach((k, v) -> data.put(k.name, k.codec.encode(v)));
    }

    public DataComponentMap read(MapData data) {
        var map = new DataComponentMap(data.size());
        data.value().forEach((k, v) -> {
            var key = get(k);
            if (key == null) return;
            var value = key.codec.decode(data);
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
            var value = key.codec.decode(ops.convertTo(DataOps.INSTANCE, p.getSecond()));
            if (value != null) map.put(key, value);
        });
        return DataResult.success(Pair.of(map, ops.empty()));
    }

    @Override
    public <T> DataResult<T> encode(DataComponentMap input, DynamicOps<T> ops, T prefix) {
        var data = new MapData(new Reference2ReferenceOpenHashMap<>());
        input.fastForEach((k, v) -> data.put(k.name, k.codec.encode(v)));
        return DataResult.success(DataOps.INSTANCE.convertMap(ops, data));
    }
}
