package com.gto.datasynclib.field.object;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.NullData;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import com.gto.datasynclib.field.AbstractField;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

public abstract class ObjField<T> extends AbstractField<T> {

    protected T lastValue;
    protected int lastHash;

    protected ObjField(DataFieldDefinition<T> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        try {
            var value = definition.field.get(source);
            var hash = Objects.hashCode(value);
            if (hash != lastHash) {
                lastHash = hash;
                return true;
            }
            return !Objects.equals(value, lastValue);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public final void writeBuf(Object source, ByteDataStream data) {
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
    public final void readBuf(Object source, ByteDataStream data) {
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
    public final Data writeToData(Object source) {
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
    public final void readFromData(Object source, Data data) {
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

    protected abstract void write(@NotNull ByteDataStream data, @NotNull T value) throws IOException;

    protected abstract @NotNull T read(@NotNull ByteDataStream data) throws IOException;

    protected abstract @NotNull Data write(@NotNull T value);

    protected abstract @NotNull T read(@NotNull Data data);
}
