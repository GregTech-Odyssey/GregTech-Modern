package com.gto.datasynclib.datasream;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.datasream.codec.ByteStreamCodec;
import com.gto.datasynclib.datasream.codec.DataCodec;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.MapData;
import com.gto.datasynclib.util.Registry;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public final class DataComponentRegistry extends Registry<String, DataComponentKey<?>> implements ByteStreamCodec<DataComponentMap>, DataCodec<DataComponentMap>, Codec<DataComponentMap> {

    public DataComponentRegistry(String name) {
        super(name + "_data_component");
    }

    public <T> DataComponentKey<T> register(String name, CombinationCodec<T> codec) {
        return register(new DataComponentKey<>(name, codec));
    }

    public <T> DataComponentKey<T> register(String name, Consumer<DataComponentKey.Builder<T>> builder) {
        return register(DataComponentKey.create(name, builder));
    }

    public <T> DataComponentKey<T> register(DataComponentKey<T> key) {
        return super.register(key.name, key);
    }

    public <T extends DataComponentKey<?>> T getDataComponentKey(String name) {
        return (T) super.get(name);
    }

    public <T extends DataComponentKey<?>> T getDataComponentKey(int id) {
        return (T) super.get(id);
    }

    @Override
    public <T> DataResult<Pair<DataComponentMap, T>> decode(DynamicOps<T> ops, T input) {
        return Data.CODEC.map(this::decode).decode(ops, input);
    }

    @Override
    public <T> DataResult<T> encode(DataComponentMap input, DynamicOps<T> ops, T prefix) {
        return Data.CODEC.comap(i -> encode(input)).encode(input, ops, prefix);
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
    public DataComponentMap decode(@NotNull Data d) {
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
    public @NotNull Data encode(DataComponentMap obj) {
        var data = new MapData();
        obj.fastForEach((k, v) -> data.put(k.name, k.codec.dataWriter.encode(v)));
        return data;
    }
}
