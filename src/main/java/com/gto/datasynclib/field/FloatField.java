package com.gto.datasynclib.field;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.FloatData;
import com.gto.datasynclib.datasream.stream.ByteDataStream;

public final class FloatField extends AbstractField<Float> {

    private float lastValue;

    public FloatField(DataFieldDefinition<Float> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        try {
            return lastValue != definition.field.getFloat(source);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeBuf(Object source, ByteDataStream data) {
        try {
            var value = definition.field.getFloat(source);
            lastValue = value;
            data.writeFloat(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readBuf(Object source, ByteDataStream data) {
        try {
            var value = data.readFloat();
            definition.field.setFloat(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Data writeToData(Object source) {
        try {
            var value = definition.field.getFloat(source);
            return FloatData.valueOf(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromData(Object source, Data data) {
        try {
            var value = data.getFloat();
            definition.field.setFloat(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
