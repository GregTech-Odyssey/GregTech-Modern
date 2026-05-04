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

public final class ArrayAccess<T> extends AbstractMarkFieldAccess<T[]> {

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
    public void writeBuffer(@NotNull LogicalSide side, T @NotNull [] instance, @NotNull FriendlyByteBuf data, boolean force) {
        for (var element : instance) {
            if (element == null) {
                data.writeBoolean(false);
            } else {
                data.writeBoolean(true);
                elementCodec.streamWriter.encode(element, data);
            }
        }
    }

    @Override
    public void readBuffer(@NotNull LogicalSide side, T @NotNull [] instance, @NotNull FriendlyByteBuf data) {
        var length = instance.length;
        for (int i = 0; i < length; i++) {
            if (data.readBoolean()) instance[i] = elementCodec.streamReader.decode(data);
        }
    }

    @Override
    public @NotNull Data writeData(T @NotNull [] instance) {
        var list = new ListData();
        for (T element : instance) {
            if (element != null) {
                list.add(elementCodec.dataWriter.encode(element));
            } else {
                list.addNull();
            }
        }
        return list;
    }

    @Override
    public void readData(T @NotNull [] instance, @NotNull Data data) {
        var list = data.getList();
        var length = Math.min(list.size(), instance.length);
        for (int i = 0; i < length; i++) {
            var d = list.get(i);
            if (d == NullData.INSTANCE) {
                instance[i] = null;
            } else {
                instance[i] = elementCodec.dataReader.decode(list.get(i));
            }
        }
    }
}
