package com.gto.datasynclib.field.object;

import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.stream.ByteDataStream;

import java.io.IOException;

public class CustomObjCodecField<T> extends ObjField<T> {

    private final CombinationCodec<T> codec;

    public CustomObjCodecField(DataFieldDefinition<T> definition, CombinationCodec<T> codec) {
        super(definition);
        this.codec = codec;
    }

    @Override
    protected final void write(ByteDataStream data, T value) throws IOException {
        codec.streamWriter.encode(value, data);
    }

    @Override
    protected final T read(ByteDataStream data) throws IOException {
        return codec.streamReader.decode(data);
    }

    @Override
    protected final Data write(T value) {
        return codec.dataWriter.encode(value);
    }

    @Override
    protected final T read(Data tag) {
        return codec.dataReader.decode(tag);
    }
}
