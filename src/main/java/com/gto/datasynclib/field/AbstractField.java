package com.gto.datasynclib.field;

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
    public final void markAsDirty(@NotNull Object source) {
        syncChange = true;
    }

    @Override
    public void clearDirty(@NotNull Object source) {
        syncChange = false;
    }

    @Override
    public boolean isDirty(@NotNull Object source) {
        return syncChange;
    }

    @Override
    public final boolean hasChanges(@NotNull LogicalSide side, @NotNull Object source, boolean auto) {
        return hasChanges(source);
    }

    protected abstract boolean hasChanges(Object source);
}
