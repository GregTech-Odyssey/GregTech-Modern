package com.gto.datasynclib.field;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.IntData;
import com.gto.datasynclib.datasream.stream.ByteDataStream;

public final class IntField extends AbstractField<Integer> {

    private int lastValue;

    public IntField(DataFieldDefinition<Integer> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        try {
            return lastValue != definition.field.getInt(source);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeBuf(Object source, ByteDataStream data) {
        try {
            var value = definition.field.getInt(source);
            lastValue = value;
            data.writeInt(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readBuf(Object source, ByteDataStream data) {
        try {
            var value = data.readInt();
            definition.field.setInt(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Data writeToData(Object source) {
        try {
            var value = definition.field.getInt(source);
            return IntData.valueOf(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromData(Object source, Data data) {
        try {
            var value = data.getInt();
            definition.field.setInt(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
