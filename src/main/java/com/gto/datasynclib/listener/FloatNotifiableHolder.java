package com.gto.datasynclib.listener;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.IDataSerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.FloatData;
import com.gto.datasynclib.util.holder.FloatHolder;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Setter
public final class FloatNotifiableHolder extends FloatHolder implements IDataSerializable, ISyncNotifiable<FloatSyncListener> {

    public static FloatNotifiableHolder create() {
        return new FloatNotifiableHolder();
    }

    private FloatSyncListener receiverListener = FloatSyncListener.EMPTY;
    private FloatSyncListener senderListener = FloatSyncListener.EMPTY;

    private float lastValue;

    private FloatNotifiableHolder() {}

    @Override
    public boolean hasChanges() {
        return value != lastValue;
    }

    @Override
    public void writeBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        data.writeFloat(value);
        senderListener.onSync(side, lastValue, value);
        lastValue = value;
    }

    @Override
    public void readBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        var oldValue = value;
        value = data.readFloat();
        receiverListener.onSync(side, oldValue, value);
    }

    @Override
    public Data writeData() {
        return FloatData.valueOf(value);
    }

    @Override
    public void readData(@NotNull Data data) {
        value = data.getFloat();
    }
}
