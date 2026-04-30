package com.gto.datasynclib.field.object;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.DataFieldDefinition;
import com.gto.datasynclib.datasream.data.Data;
import org.jetbrains.annotations.NotNull;

public class ObjCodecField<T> extends ObjField<T> {

    public ObjCodecField(DataFieldDefinition<T> definition) {
        super(definition);
    }

    @Override
    protected final void write(@NotNull FriendlyByteBuf data, @NotNull T value) {
        definition.codec.streamWriter.encode(value, data);
    }

    @Override
    protected final @NotNull T read(@NotNull FriendlyByteBuf data) {
        return definition.codec.streamReader.decode(data);
    }

    @Override
    protected final @NotNull Data write(@NotNull T value) {
        return definition.codec.dataWriter.encode(value);
    }

    @Override
    protected final @NotNull T read(@NotNull Data data) {
        return definition.codec.dataReader.decode(data);
    }
}
