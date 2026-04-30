package com.gto.datasynclib.field.access;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.ListData;
import com.gto.datasynclib.datasream.data.NullData;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class ArrayAccess<T> extends AbstractFieldAccess<T[]> {

    private final CombinationCodec<T> elementCodec;
    private int hashCode;

    public ArrayAccess(DataFieldDefinition<T[]> definition, CombinationCodec<T> elementCodec) {
        super(definition);
        this.elementCodec = elementCodec;
    }

    @Override
    public boolean hasChanges(@NotNull LogicalSide side, @NotNull Object source, boolean auto) {
        var hashCode = Arrays.hashCode(getInstance(source));
        if (hashCode != this.hashCode) {
            this.hashCode = hashCode;
            return true;
        }
        return false;
    }

    @Override
    public void writeToBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data, boolean force) {
        var array = getInstance(source);
        for (var element : array) {
            if (element == null) {
                data.writeBoolean(false);
            } else {
                data.writeBoolean(true);
                elementCodec.streamWriter.encode(element, data);
            }
        }
    }

    @Override
    public void readFromBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data) {
        var array = getInstance(source);
        var length = array.length;
        for (int i = 0; i < length; i++) {
            if (data.readBoolean()) array[i] = elementCodec.streamReader.decode(data);
        }
    }

    @Override
    public @NotNull Data writeToData(@NotNull Object source) {
        var list = new ListData();
        var array = getInstance(source);
        for (T element : array) {
            if (element != null) {
                list.add(elementCodec.dataWriter.encode(element));
            } else {
                list.addNull();
            }
        }
        return list;
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data) {
        var list = data.getList();
        var array = getInstance(source);
        var length = Math.min(list.size(), array.length);
        for (int i = 0; i < length; i++) {
            var d = list.get(i);
            if (d == NullData.INSTANCE) {
                array[i] = null;
            } else {
                array[i] = elementCodec.dataReader.decode(list.get(i));
            }
        }
    }
}
