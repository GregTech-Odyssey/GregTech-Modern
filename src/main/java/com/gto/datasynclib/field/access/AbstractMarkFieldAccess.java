package com.gto.datasynclib.field.access;

import com.gto.datasynclib.DataFieldDefinition;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractMarkFieldAccess<T> extends AbstractFieldAccess<T> {

    protected boolean syncChange = true;

    protected AbstractMarkFieldAccess(DataFieldDefinition<T> definition) {
        super(definition);
    }

    @Override
    public final void markAsDirty(@NotNull Object source) {
        syncChange = true;
    }

    @Override
    public final void clearDirty(@NotNull Object source) {
        syncChange = false;
    }

    @Override
    public final boolean isDirty(@NotNull Object source) {
        return syncChange;
    }
}
