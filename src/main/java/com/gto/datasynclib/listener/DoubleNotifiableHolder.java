package com.gto.datasynclib.listener;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.ISerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.DoubleData;
import com.gto.datasynclib.util.holder.DoubleHolder;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Setter
public final class DoubleNotifiableHolder extends DoubleHolder implements ISerializable, ISyncNotifiable<DoubleSyncListener> {

    public static DoubleNotifiableHolder create() {
        return new DoubleNotifiableHolder();
    }

    private DoubleSyncListener receiverListener = DoubleSyncListener.EMPTY;
    private DoubleSyncListener senderListener = DoubleSyncListener.EMPTY;

    private double lastValue;

    private DoubleNotifiableHolder() {}

    @Override
    public boolean hasChanges() {
        return value != lastValue;
    }

    @Override
    public void writeBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        data.writeDouble(value);
        senderListener.onSync(side, lastValue, value);
        lastValue = value;
    }

    @Override
    public void readBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        var oldValue = value;
        value = data.readDouble();
        receiverListener.onSync(side, oldValue, value);
    }

    @Override
    public Data writeData() {
        return DoubleData.valueOf(value);
    }

    @Override
    public void readData(@NotNull Data data) {
        value = data.getDouble();
    }
}
