package com.gto.datasynclib.listener;

import com.gto.datasynclib.ISerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.IntData;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import com.gto.datasynclib.util.holder.IntHolder;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@Setter
public final class IntNotifiableHolder extends IntHolder implements ISerializable, ISyncNotifiable<IntSyncListener> {

    public static IntNotifiableHolder create() {
        return new IntNotifiableHolder();
    }

    private IntSyncListener receiverListener = IntSyncListener.EMPTY;
    private IntSyncListener senderListener = IntSyncListener.EMPTY;

    private int lastValue;

    private IntNotifiableHolder() {}

    @Override
    public boolean hasChanges() {
        return value != lastValue;
    }

    @Override
    public void writeBuf(LogicalSide side, @NotNull ByteDataStream data) throws IOException {
        data.writeInt(value);
        senderListener.onSync(side, lastValue, value);
        lastValue = value;
    }

    @Override
    public void readBuf(LogicalSide side, @NotNull ByteDataStream data) throws IOException {
        var oldValue = value;
        value = data.readInt();
        receiverListener.onSync(side, oldValue, value);
    }

    @Override
    public Data writeData() {
        return IntData.valueOf(value);
    }

    @Override
    public void readData(@NotNull Data data) {
        value = data.getInt();
    }
}
