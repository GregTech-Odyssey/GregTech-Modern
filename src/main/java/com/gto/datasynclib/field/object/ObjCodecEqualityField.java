package com.gto.datasynclib.field.object;

import com.gto.datasynclib.DataFieldDefinition;
import it.unimi.dsi.fastutil.Hash;

public final class ObjCodecEqualityField<T> extends ObjCodecField<T> {

    public final Hash.Strategy<T> strategy;

    public ObjCodecEqualityField(DataFieldDefinition<T> definition, Hash.Strategy<T> strategy) {
        super(definition);
        this.strategy = strategy;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean hasChanges(Object source) {
        try {
            return strategy.equals((T) definition.field.get(source), lastValue);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
