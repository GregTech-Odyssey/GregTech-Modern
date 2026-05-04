package com.gto.datasynclib.datasream;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.datasream.codec.ByteStreamCodec;
import com.gto.datasynclib.datasream.codec.DataCodec;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.DataOps;
import com.gto.datasynclib.datasream.data.MapData;
import com.gto.datasynclib.util.Registry;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.stream.Stream;

public final class DataComponentRegistry extends Registry<String, DataComponentKey<?>> implements ByteStreamCodec<DataComponentMap>, DataCodec<DataComponentMap>, Codec<DataComponentMap> {

    public DataComponentRegistry(String name) {
        super(name + "_data_component");
    }

    public <T> DataComponentKey<T> register(String key, CombinationCodec<T> codec) {
        return register(new DataComponentKey<>(key, codec));
    }

    public <T> DataComponentKey<T> register(DataComponentKey<T> key) {
        return (DataComponentKey<T>) super.register(key.name, key);
    }

    public <T> DataComponentKey<T> getDataComponentKey(String name) {
        return (DataComponentKey<T>) super.get(name);
    }

    public <T> DataComponentKey<T> getDataComponentKey(int id) {
        return (DataComponentKey<T>) super.get(id);
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

    @Override
    public DataComponentMap decode(FriendlyByteBuf buf) {
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

    @Override
    public void encode(FriendlyByteBuf buf, DataComponentMap obj) {
        buf.writeVarInt(obj.size());
        obj.fastForEach((k, v) -> {
            buf.writeVarInt(getId(k));
            k.codec.streamWriter.encode(buf, v);
        });
    }

    @Override
    public DataComponentMap decode(Data d) {
        var data = d.getMap();
        var map = new DataComponentMap(data.size());
        data.forEach((k, v) -> {
            var key = get(k);
            if (key == null) return;
            var value = key.codec.dataReader.decode(v);
            if (value != null) map.put(key, value);
        });
        return map;
    }

    @Override
    public Data encode(DataComponentMap obj) {
        var data = new MapData();
        obj.fastForEach((k, v) -> data.put(k.name, k.codec.dataWriter.encode(v)));
        return data;
    }
}
