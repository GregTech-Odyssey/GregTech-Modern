package com.gto.datasynclib.field.object;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.Data;
import org.jetbrains.annotations.NotNull;

public class CustomObjCodecField<T> extends ObjField<T> {

    private final CombinationCodec<T> codec;

    public CustomObjCodecField(DataFieldDefinition<T> definition, CombinationCodec<T> codec) {
        super(definition);
        this.codec = codec;
    }

    @Override
    protected final void write(@NotNull FriendlyByteBuf data, @NotNull T value) {
        codec.streamWriter.encode(value, data);
    }

    @Override
    protected final @NotNull T read(@NotNull FriendlyByteBuf data) {
        return codec.streamReader.decode(data);
    }

    @Override
    protected final @NotNull Data write(@NotNull T value) {
        return codec.dataWriter.encode(value);
    }

    @Override
    protected final @NotNull T read(@NotNull Data tag) {
        return codec.dataReader.decode(tag);
    }
}
