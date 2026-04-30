package com.gto.datasynclib.field;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataField;
import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.LogicalSide;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractField<T> implements DataField<T> {

    @Getter
    protected final DataFieldDefinition<T> definition;

    protected boolean syncChange = true;

    protected AbstractField(DataFieldDefinition<T> definition) {
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
    public final boolean hasChanges(@NotNull LogicalSide side, @NotNull Object source, boolean auto) {
        return hasChanges(source);
    }

    protected abstract boolean hasChanges(Object source);

    @Override
    public final void writeToBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data, boolean force) {
        writeBuf(source, data);
    }

    @Override
    public final void readFromBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull FriendlyByteBuf data) {
        readBuf(source, data);
    }

    protected abstract void writeBuf(@NotNull Object source, @NotNull FriendlyByteBuf data);

    protected abstract void readBuf(@NotNull Object source, @NotNull FriendlyByteBuf data);
}
