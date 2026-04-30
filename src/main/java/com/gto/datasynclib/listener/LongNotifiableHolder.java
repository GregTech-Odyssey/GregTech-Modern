package com.gto.datasynclib.listener;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.ISerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.LongData;
import com.gto.datasynclib.util.holder.LongHolder;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Setter
public final class LongNotifiableHolder extends LongHolder implements ISerializable, ISyncNotifiable<LongSyncListener> {

    public static LongNotifiableHolder create() {
        return new LongNotifiableHolder();
    }

    private LongSyncListener receiverListener = LongSyncListener.EMPTY;
    private LongSyncListener senderListener = LongSyncListener.EMPTY;

    private long lastValue;

    private LongNotifiableHolder() {}

    @Override
    public boolean hasChanges() {
        return value != lastValue;
    }

    @Override
    public void writeBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        data.writeLong(value);
        senderListener.onSync(side, lastValue, value);
        lastValue = value;
    }

    @Override
    public void readBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        var oldValue = value;
        value = data.readLong();
        receiverListener.onSync(side, oldValue, value);
    }

    @Override
    public Data writeData() {
        return LongData.valueOf(value);
    }

    @Override
    public void readData(@NotNull Data data) {
        value = data.getLong();
    }
}
