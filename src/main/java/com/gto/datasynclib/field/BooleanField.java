package com.gto.datasynclib.field;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.BooleanData;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.stream.ByteDataStream;

public final class BooleanField extends AbstractField<Boolean> {

    private boolean lastValue;

    public BooleanField(DataFieldDefinition<Boolean> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        try {
            return lastValue != definition.field.getBoolean(source);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeBuf(Object source, ByteDataStream data) {
        try {
            var value = definition.field.getBoolean(source);
            lastValue = value;
            data.writeBoolean(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readBuf(Object source, ByteDataStream data) {
        try {
            var value = data.readBoolean();
            definition.field.setBoolean(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Data writeToData(Object source) {
        try {
            var value = definition.field.getBoolean(source);
            return BooleanData.valueOf(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromData(Object source, Data data) {
        try {
            var value = data.getBoolean();
            definition.field.setBoolean(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
