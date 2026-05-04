package com.gto.datasynclib.field.access;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.ByteArrayData;
import com.gto.datasynclib.datasream.data.Data;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class ByteArrayAccess<T> extends AbstractMarkFieldAccess<byte[]> {

    private int hashCode;

    public ByteArrayAccess(DataFieldDefinition<byte[]> definition) {
        super(definition);
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
    public void writeBuffer(@NotNull LogicalSide side, byte @NotNull [] instance, @NotNull FriendlyByteBuf data, boolean force) {
        for (var element : instance) {
            data.writeByte(element);
        }
    }

    @Override
    public void readBuffer(@NotNull LogicalSide side, byte @NotNull [] instance, @NotNull FriendlyByteBuf data) {
        var length = instance.length;
        for (int i = 0; i < length; i++) {
            instance[i] = data.readByte();
        }
    }

    @Override
    public @NotNull Data writeData(byte @NotNull [] instance) {
        return ByteArrayData.valueOf(instance);
    }

    @Override
    public void readData(byte @NotNull [] instance, @NotNull Data data) {
        var list = data.getByteArray();
        var length = Math.min(list.length, instance.length);
        System.arraycopy(list, 0, instance, 0, length);
    }
}
