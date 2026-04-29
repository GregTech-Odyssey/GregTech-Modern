package com.gto.datasynclib.field;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.DoubleData;
import com.gto.datasynclib.datasream.stream.ByteDataStream;

public final class DoubleField extends AbstractField<Double> {

    private double lastValue;

    public DoubleField(DataFieldDefinition<Double> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        try {
            return lastValue != definition.field.getDouble(source);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeBuf(Object source, ByteDataStream data) {
        try {
            var value = definition.field.getDouble(source);
            lastValue = value;
            data.writeDouble(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readBuf(Object source, ByteDataStream data) {
        try {
            var value = data.readDouble();
            definition.field.setDouble(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Data writeToData(Object source) {
        try {
            var value = definition.field.getDouble(source);
            return DoubleData.valueOf(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromData(Object source, Data data) {
        try {
            var value = data.getDouble();
            definition.field.setDouble(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
