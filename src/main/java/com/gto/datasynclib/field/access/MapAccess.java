package com.gto.datasynclib.field.access;

import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.ListData;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
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
    protected boolean hasChanges(@NotNull LogicalSide side, boolean auto) {
        var hashCode = getInstance().hashCode();
        if (hashCode != this.hashCode) {
            this.hashCode = hashCode;
            return true;
        }
        return false;
    }

    @Override
    protected void writeBuf(@NotNull LogicalSide side, @NotNull ByteDataStream data, boolean force) throws IOException {
        var map = getInstance();
        data.writeVarInt(map.size());
        map.forEach((k, v) -> {
            try {
                keyCodec.streamWriter.encode((K) k, data);
                valueCodec.streamWriter.encode((V) v, data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    protected void readBuf(@NotNull LogicalSide side, @NotNull ByteDataStream data) throws IOException {
        var length = data.readVarInt();
        var map = getInstance();
        map.clear();
        for (int i = 0; i < length; i++) {
            K key = keyCodec.streamReader.decode(data);
            V value = valueCodec.streamReader.decode(data);
            map.put(key, value);
        }
    }

    @Override
    protected Data writeData() {
        var list = new ListData();
        getInstance().forEach((k, v) -> {
            list.add(keyCodec.dataWriter.encode((K) k));
            list.add(valueCodec.dataWriter.encode((V) v));
        });
        return list;
    }

    @Override
    protected void readData(@NotNull Data data) {
        var list = data.getList();
        var size = list.size();
        var map = getInstance();
        map.clear();
        for (int i = 0; i < size; i++) {
            map.put(keyCodec.dataReader.decode(list.get(i++)), valueCodec.dataReader.decode(list.get(i)));
        }
    }
}
