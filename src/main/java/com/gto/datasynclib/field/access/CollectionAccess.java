package com.gto.datasynclib.field.access;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.ListData;
import com.gto.datasynclib.datasream.data.NullData;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class CollectionAccess<E> extends AbstractFieldAccess<Collection> {

    private final CombinationCodec<E> elementCodec;
    private int hashCode;

    @SuppressWarnings("unchecked")
    public CollectionAccess(DataFieldDefinition<Collection> definition) {
        super(definition);
        if (definition.genericCodec.length == 0) throw new IllegalArgumentException("Collection type not found");
        this.elementCodec = (CombinationCodec<E>) definition.genericCodec[0];
        if (this.elementCodec == null) throw new IllegalArgumentException("Codec not found for type " + definition.genericType[0]);
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
    public void writeBuffer(@NotNull LogicalSide side, @NotNull Collection instance, @NotNull FriendlyByteBuf data, boolean force) {
        data.writeVarInt(instance.size());
        for (var element : instance) {
            elementCodec.streamWriter.encode((E) element, data);
        }
    }

    @Override
    public void readBuffer(@NotNull LogicalSide side, @NotNull Collection instance, @NotNull FriendlyByteBuf data) {
        var length = data.readVarInt();
        instance.clear();
        for (int i = 0; i < length; i++) {
            instance.add(elementCodec.streamReader.decode(data));
        }
    }

    @Override
    public @NotNull Data writeData(@NotNull Collection instance) {
        var list = new ListData();
        for (var element : instance) {
            if (element == null) {
                list.addNull();
            } else {
                list.add(elementCodec.dataWriter.encode((E) element));
            }
        }
        return list;
    }

    @Override
    public void readData(@NotNull Collection instance, @NotNull Data data) {
        var list = data.getList();
        instance.clear();
        for (var value : list) {
            if (value == NullData.INSTANCE) {
                instance.add(null);
            } else {
                instance.add(elementCodec.dataReader.decode(value));
            }
        }
    }
}
