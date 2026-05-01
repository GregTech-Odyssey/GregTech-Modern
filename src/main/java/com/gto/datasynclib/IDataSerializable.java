package com.gto.datasynclib;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datasream.data.Data;
import org.jetbrains.annotations.NotNull;

public interface IDataSerializable {

    boolean hasChanges();

    void writeBuf(LogicalSide side, @NotNull FriendlyByteBuf data);

    void readBuf(LogicalSide side, @NotNull FriendlyByteBuf data);

    Data writeData();

    void readData(@NotNull Data tag);
}
