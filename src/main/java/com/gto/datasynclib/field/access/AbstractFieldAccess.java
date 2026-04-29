package com.gto.datasynclib.field.access;

import com.gto.datasynclib.DataField;
import com.gto.datasynclib.DataFieldDefinition;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

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

    @NotNull
    protected T getInstance(Object source) {
        if (definition.isFinal) {
            if (instance != null) return instance;
            return instance = get(source);
        }
        return get(source);
    }

    private T get(Object source) {
        try {
            return (T) definition.field.get(source);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
