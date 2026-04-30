package com.gto.datasynclib.field;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.IntData;
import org.jetbrains.annotations.NotNull;

public final class IntField extends AbstractField<Integer> {

    private int lastValue;

    public IntField(DataFieldDefinition<Integer> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        try {
            return lastValue != definition.field.getInt(source);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeBuf(@NotNull Object source, @NotNull FriendlyByteBuf data) {
        try {
            var value = definition.field.getInt(source);
            lastValue = value;
            data.writeInt(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readBuf(@NotNull Object source, @NotNull FriendlyByteBuf data) {
        try {
            var value = data.readInt();
            definition.field.setInt(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public @NotNull Data writeToData(@NotNull Object source) {
        try {
            var value = definition.field.getInt(source);
            return IntData.valueOf(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromData(@NotNull Object source, @NotNull Data data) {
        try {
            var value = data.getInt();
            definition.field.setInt(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
