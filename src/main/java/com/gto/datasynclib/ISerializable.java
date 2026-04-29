package com.gto.datasynclib;

import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public interface ISerializable {

    boolean hasChanges();

    void writeBuf(LogicalSide side, @NotNull ByteDataStream data) throws IOException;

    void readBuf(LogicalSide side, @NotNull ByteDataStream data) throws IOException;

    Data writeData();

    void readData(@NotNull Data tag);
}
