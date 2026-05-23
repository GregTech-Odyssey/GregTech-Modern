package com.gto.datasynclib.field.access;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.ListData;
import com.gto.datasynclib.datasream.data.LongData;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMaps;
import org.jetbrains.annotations.NotNull;

public class Reference2IntMapAccess<K> extends AbstractMarkFieldAccess<Reference2IntMap> {

    private final CombinationCodec<K> keyCodec;
    private int hashCode;

    @SuppressWarnings("unchecked")
    public Reference2IntMapAccess(DataFieldDefinition<Reference2IntMap> definition) {
        super(definition);
        if (definition.genericType.length < 1) throw new IllegalArgumentException("Map type parameters not found");
        this.keyCodec = (CombinationCodec<K>) definition.genericCodec[0];
        if (this.keyCodec == null) throw new IllegalArgumentException("Codec not found for key type " + definition.genericType[0]);
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
    public void writeBuffer(@NotNull LogicalSide side, @NotNull Reference2IntMap instance, @NotNull FriendlyByteBuf data, boolean force) {
        data.writeVarInt(instance.size());
        Reference2IntMaps.fastForEach(instance, e -> {
            keyCodec.streamWriter.encode((K) e.getKey(), data);
            data.writeLong(e.getIntValue());
        });
    }

    @Override
    public void readBuffer(@NotNull LogicalSide side, @NotNull Reference2IntMap instance, @NotNull FriendlyByteBuf data) {
        var length = data.readVarInt();
        instance.clear();
        for (int i = 0; i < length; i++) {
            var key = keyCodec.streamReader.decode(data);
            var value = data.readInt();
            instance.put(key, value);
        }
    }

    @Override
    public @NotNull Data writeData(@NotNull Reference2IntMap instance) {
        var list = new ListData();
        Reference2IntMaps.fastForEach(instance, e -> {
            list.add(keyCodec.dataWriter.encode((K) e.getKey()));
            list.add(LongData.valueOf(e.getIntValue()));
        });
        return list;
    }

    @Override
    public void readData(@NotNull Reference2IntMap instance, @NotNull Data data) {
        var list = data.getList();
        var size = list.size();
        instance.clear();
        for (int i = 0; i < size; i++) {
            instance.put(keyCodec.dataReader.decode(list.get(i++)), list.get(i).getInt());
        }
    }
}
