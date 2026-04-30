package com.gto.datasynclib.field.access;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.IntArrayData;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class IntArrayAccess<T> extends AbstractFieldAccess<int[]> {

    private int hashCode;

    public IntArrayAccess(DataFieldDefinition<int[]> definition) {
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
    public void writeBuffer(@NotNull LogicalSide side, int @NotNull [] instance, @NotNull FriendlyByteBuf data, boolean force) {
        for (var element : instance) {
            data.writeInt(element);
        }
    }

    @Override
    public void readBuffer(@NotNull LogicalSide side, int @NotNull [] instance, @NotNull FriendlyByteBuf data) {
        var length = instance.length;
        for (int i = 0; i < length; i++) {
            instance[i] = data.readInt();
        }
    }

    @Override
    public @NotNull Data writeData(int @NotNull [] instance) {
        return IntArrayData.valueOf(instance);
    }

    @Override
    public void readData(int @NotNull [] instance, @NotNull Data data) {
        var list = data.getIntArray();
        var length = Math.min(list.length, instance.length);
        System.arraycopy(list, 0, instance, 0, length);
    }
}
