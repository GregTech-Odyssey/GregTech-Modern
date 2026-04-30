package com.gto.datasynclib.field.object;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.NullData;
import com.gto.datasynclib.field.AbstractField;
import org.jetbrains.annotations.NotNull;

public abstract class ObjField<T> extends AbstractField<T> {

    protected T lastValue;
    protected int lastHash;

    protected ObjField(DataFieldDefinition<T> definition) {
        super(definition);
    }

    @Override
    @SuppressWarnings("unchecked")
    public final boolean hasChanges(Object source) {
        try {
            var value = definition.field.get(source);
            var hash = definition.strategy.hashCode((T) value);
            if (hash != lastHash) {
                lastHash = hash;
                return true;
            }
            return !definition.strategy.equals((T) value, lastValue);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public final void writeBuf(@NotNull Object source, @NotNull FriendlyByteBuf data) {
        try {
            T value = (T) definition.field.get(source);
            updateLastValue(value);
            if (value == null) {
                data.writeBoolean(false);
            } else {
                data.writeBoolean(true);
                write(data, value);
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public final void readBuf(@NotNull Object source, @NotNull FriendlyByteBuf data) {
        try {
            T value;
            if (data.readBoolean()) {
                value = read(data);
            } else {
                value = null;
            }
            definition.field.set(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public final @NotNull Data writeToData(@NotNull Object source) {
        try {
            T value = (T) definition.field.get(source);
            if (value == null) {
                return NullData.INSTANCE;
            } else {
                return write(value);
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public final void readFromData(@NotNull Object source, @NotNull Data data) {
        try {
            T value;
            if (data == NullData.INSTANCE) {
                value = null;
            } else {
                value = read(data);
            }
            definition.field.set(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    protected void updateLastValue(T value) {
        lastValue = value;
    }

    protected abstract void write(@NotNull FriendlyByteBuf data, @NotNull T value);

    protected abstract @NotNull T read(@NotNull FriendlyByteBuf data);

    protected abstract @NotNull Data write(@NotNull T value);

    protected abstract @NotNull T read(@NotNull Data data);
}
