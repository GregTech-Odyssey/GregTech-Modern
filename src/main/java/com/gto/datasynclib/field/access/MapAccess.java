package com.gto.datasynclib.field.access;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.ListData;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class MapAccess<K, V> extends AbstractFieldAccess<Map> {

    private final CombinationCodec<K> keyCodec;
    private final CombinationCodec<V> valueCodec;
    private int hashCode;

    @SuppressWarnings("unchecked")
    public MapAccess(DataFieldDefinition<Map> definition) {
        super(definition);
        if (definition.genericType.length < 2) throw new IllegalArgumentException("Map type parameters not found");
        this.keyCodec = (CombinationCodec<K>) definition.genericCodec[0];
        this.valueCodec = (CombinationCodec<V>) definition.genericCodec[1];
        if (this.keyCodec == null) throw new IllegalArgumentException("Codec not found for key type " + definition.genericType[0]);
        if (this.valueCodec == null) throw new IllegalArgumentException("Codec not found for value type " + definition.genericType[1]);
    }

    @Override
    public boolean hasChanges(@NotNull LogicalSide side, @NotNull Object source, boolean auto) {
        var hashCode = getInstance(source).hashCode();
        if (hashCode != this.hashCode) {
            this.hashCode = hashCode;
            return true;
        }
        return false;
    }

    @Override
    public void writeToBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data, boolean force) {
        var map = getInstance(source);
        data.writeVarInt(map.size());
        map.forEach((k, v) -> {
            keyCodec.streamWriter.encode((K) k, data);
            valueCodec.streamWriter.encode((V) v, data);
        });
    }

    @Override
    public void readFromBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data) {
        var length = data.readVarInt();
        var map = getInstance(source);
        map.clear();
        for (int i = 0; i < length; i++) {
            K key = keyCodec.streamReader.decode(data);
            V value = valueCodec.streamReader.decode(data);
            map.put(key, value);
        }
    }

    @Override
    public @NotNull Data writeToData(@NotNull Object source) {
        var list = new ListData();
        getInstance(source).forEach((k, v) -> {
            list.add(keyCodec.dataWriter.encode((K) k));
            list.add(valueCodec.dataWriter.encode((V) v));
        });
        return list;
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data) {
        var list = data.getList();
        var size = list.size();
        var map = getInstance(source);
        map.clear();
        for (int i = 0; i < size; i++) {
            map.put(keyCodec.dataReader.decode(list.get(i++)), valueCodec.dataReader.decode(list.get(i)));
        }
    }
}
