package com.gto.datasynclib.field.access;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.codec.DataCodec;
import com.gto.datasynclib.datasream.data.Data;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class BooleanArrayAccess<T> extends AbstractFieldAccess<boolean[]> {

    private int hashCode;

    public BooleanArrayAccess(DataFieldDefinition<boolean[]> definition) {
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
    public void writeBuffer(@NotNull LogicalSide side, boolean @NotNull [] instance, @NotNull FriendlyByteBuf data, boolean force) {
        for (var element : instance) {
            data.writeBoolean(element);
        }
    }

    @Override
    public void readBuffer(@NotNull LogicalSide side, boolean @NotNull [] instance, @NotNull FriendlyByteBuf data) {
        var length = instance.length;
        for (int i = 0; i < length; i++) {
            instance[i] = data.readBoolean();
        }
    }

    @Override
    public @NotNull Data writeData(boolean @NotNull [] instance) {
        return DataCodec.BOOLEANS_CODEC.encode(instance);
    }

    @Override
    public void readData(boolean @NotNull [] instance, @NotNull Data data) {
        var list = DataCodec.BOOLEANS_CODEC.decode(data);
        var length = Math.min(list.length, instance.length);
        System.arraycopy(list, 0, instance, 0, length);
    }
}
