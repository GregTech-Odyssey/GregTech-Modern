package com.gto.datasynclib.field;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.CharData;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.stream.ByteDataStream;

public final class CharField extends AbstractField<Character> {

    private char lastValue;

    public CharField(DataFieldDefinition<Character> definition) {
        super(definition);
    }

    @Override
    public boolean hasChanges(Object source) {
        try {
            return lastValue != definition.field.getChar(source);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeBuf(Object source, ByteDataStream data) {
        try {
            var value = definition.field.getChar(source);
            lastValue = value;
            data.writeChar(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readBuf(Object source, ByteDataStream data) {
        try {
            var value = data.readChar();
            definition.field.setChar(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Data writeToData(Object source) {
        try {
            var value = definition.field.getChar(source);
            return CharData.valueOf(value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void readFromData(Object source, Data data) {
        try {
            char value = data.getChar();
            definition.field.setChar(source, value);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
