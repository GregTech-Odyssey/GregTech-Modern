package com.gto.datasynclib.field;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.LongData;
import com.gto.datasynclib.datasream.stream.ByteDataStream;

public final class LongField extends AbstractField<Long> {

    private long lastValue;

    public LongField(DataFieldDefinition<Long> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        try {
            return lastValue != definition.field.getLong(source);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeBuf(Object source, ByteDataStream data) {
        try {
            var value = definition.field.getLong(source);
            lastValue = value;
            data.writeLong(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readBuf(Object source, ByteDataStream data) {
        try {
            var value = data.readLong();
            definition.field.setLong(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Data writeToData(Object source) {
        try {
            var value = definition.field.getLong(source);
            return LongData.valueOf(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromData(Object source, Data data) {
        try {
            var value = data.getLong();
            definition.field.setLong(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
