package com.gto.datasynclib.listener;

import com.gto.datasynclib.ISerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.ShortData;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import com.gto.datasynclib.util.holder.ShortHolder;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@Setter
public final class ShortNotifiableHolder extends ShortHolder implements ISerializable, ISyncNotifiable<ShortSyncListener> {

    public static ShortNotifiableHolder create() {
        return new ShortNotifiableHolder();
    }

    private ShortSyncListener receiverListener = ShortSyncListener.EMPTY;
    private ShortSyncListener senderListener = ShortSyncListener.EMPTY;

    private short lastValue;

    private ShortNotifiableHolder() {}

    @Override
    public boolean hasChanges() {
        return value != lastValue;
    }

    @Override
    public void writeBuf(LogicalSide side, @NotNull ByteDataStream data) throws IOException {
        data.writeShort(value);
        senderListener.onSync(side, lastValue, value);
        lastValue = value;
    }

    @Override
    public void readBuf(LogicalSide side, @NotNull ByteDataStream data) throws IOException {
        var oldValue = value;
        value = data.readShort();
        receiverListener.onSync(side, oldValue, value);
    }

    @Override
    public Data writeData() {
        return ShortData.valueOf(value);
    }

    @Override
    public void readData(@NotNull Data data) {
        value = data.getShort();
    }
}
