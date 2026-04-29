package com.gto.datasynclib.listener;

import com.gto.datasynclib.ISerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.ByteData;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import com.gto.datasynclib.util.holder.ByteHolder;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@Setter
public final class ByteNotifiableHolder extends ByteHolder implements ISerializable, ISyncNotifiable<ByteSyncListener> {

    public static ByteNotifiableHolder create() {
        return new ByteNotifiableHolder();
    }

    private ByteSyncListener receiverListener = ByteSyncListener.EMPTY;
    private ByteSyncListener senderListener = ByteSyncListener.EMPTY;

    private byte lastValue;

    private ByteNotifiableHolder() {}

    @Override
    public boolean hasChanges() {
        return value != lastValue;
    }

    @Override
    public void writeBuf(LogicalSide side, @NotNull ByteDataStream data) throws IOException {
        data.writeByte(value);
        senderListener.onSync(side, lastValue, value);
        lastValue = value;
    }

    @Override
    public void readBuf(LogicalSide side, @NotNull ByteDataStream data) throws IOException {
        var oldValue = value;
        value = data.readByte();
        receiverListener.onSync(side, oldValue, value);
    }

    @Override
    public Data writeData() {
        return ByteData.valueOf(value);
    }

    @Override
    public void readData(@NotNull Data data) {
        value = data.getByte();
    }
}
