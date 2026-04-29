package com.gto.datasynclib.field.access;

import com.gto.datasynclib.DataField;
import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class AbstractFieldAccess<T> implements DataField<T> {

    @Getter
    protected final DataFieldDefinition<T> definition;

    protected boolean syncChange = true;

    private T instance;

    protected AbstractFieldAccess(DataFieldDefinition<T> definition) {
        this.definition = definition;
    }

    @Override
    public final void markAsDirty() {
        syncChange = true;
    }

    @Override
    public void clearDirty() {
        syncChange = false;
    }

    @Override
    public boolean isDirty() {
        return syncChange;
    }

    @Override
    public final boolean hasChanges(@NotNull LogicalSide side, Object source, boolean auto) {
        if (instance == null) updateInstance(source);
        return hasChanges(side, auto);
    }

    protected abstract boolean hasChanges(@NotNull LogicalSide side, boolean auto);

    @Override
    public final void writeToBuffer(LogicalSide side, @NotNull Object source, @NotNull ByteDataStream data, boolean force) throws IOException {
        if (instance == null) updateInstance(source);
        writeBuf(side, data, force);
    }

    protected abstract void writeBuf(@NotNull LogicalSide side, @NotNull ByteDataStream data, boolean force) throws IOException;

    @Override
    public final void readFromBuffer(LogicalSide side, @NotNull Object source, @NotNull ByteDataStream data) throws IOException {
        if (instance == null) updateInstance(source);
        readBuf(side, data);
    }

    protected abstract void readBuf(@NotNull LogicalSide side, @NotNull ByteDataStream data) throws IOException;

    @Override
    public final Data writeToData(@NotNull Object source) {
        if (instance == null) updateInstance(source);
        return writeData();
    }

    protected abstract Data writeData();

    @Override
    public final void readFromData(@NotNull Object source, @NotNull Data data) {
        if (instance == null) updateInstance(source);
        readData(data);
    }

    protected abstract void readData(@NotNull Data data);

    private void updateInstance(Object source) {
        try {
            instance = (T) definition.field.get(source);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    protected T getInstance() {
        return instance;
    }
}
