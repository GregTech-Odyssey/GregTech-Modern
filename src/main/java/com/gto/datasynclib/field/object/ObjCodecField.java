package com.gto.datasynclib.field.object;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.stream.ByteDataStream;

import java.io.IOException;

public class ObjCodecField<T> extends ObjField<T> {

    public ObjCodecField(DataFieldDefinition<T> definition) {
        super(definition);
    }

    @Override
    protected final void write(ByteDataStream data, T value) throws IOException {
        definition.codec.streamWriter.encode(value, data);
    }

    @Override
    protected final T read(ByteDataStream data) throws IOException {
        return definition.codec.streamReader.decode(data);
    }

    @Override
    protected final Data write(T value) {
        return definition.codec.dataWriter.encode(value);
    }

    @Override
    protected final T read(Data data) {
        return definition.codec.dataReader.decode(data);
    }
}
