package com.gto.datasynclib.field.access;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.LongArrayData;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class LongArrayAccess extends AbstractMarkFieldAccess<long[]> {

    private int hashCode;

    public LongArrayAccess(DataFieldDefinition<long[]> definition) {
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
    public void writeBuffer(@NotNull LogicalSide side, long @NotNull [] instance, @NotNull FriendlyByteBuf data, boolean force) {
        for (var element : instance) {
            data.writeLong(element);
        }
    }

    @Override
    public void readBuffer(@NotNull LogicalSide side, long @NotNull [] instance, @NotNull FriendlyByteBuf data) {
        var length = instance.length;
        for (int i = 0; i < length; i++) {
            instance[i] = data.readLong();
        }
    }

    @Override
    public @NotNull Data writeData(long @NotNull [] instance) {
        return LongArrayData.valueOf(instance);
    }

    @Override
    public void readData(long @NotNull [] instance, @NotNull Data data) {
        var list = data.getLongArray();
        var length = Math.min(list.length, instance.length);
        System.arraycopy(list, 0, instance, 0, length);
    }
}
