package com.gto.datasynclib.listener;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.IDataSerializable;
import com.gto.datasynclib.LogicalSide;
import com.gto.datasynclib.datasream.data.BooleanData;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.util.holder.BooleanHolder;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Setter
public final class BooleanNotifiableHolder extends BooleanHolder implements IDataSerializable, ISyncNotifiable<BooleanSyncListener> {

    public static BooleanNotifiableHolder create() {
        return new BooleanNotifiableHolder();
    }

    private BooleanSyncListener receiverListener = BooleanSyncListener.EMPTY;
    private BooleanSyncListener senderListener = BooleanSyncListener.EMPTY;

    private boolean lastValue;

    private BooleanNotifiableHolder() {}

    @Override
    public boolean hasChanges() {
        return value != lastValue;
    }

    @Override
    public void writeBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        data.writeBoolean(value);
        senderListener.onSync(side, lastValue, value);
        lastValue = value;
    }

    @Override
    public void readBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        var oldValue = value;
        value = data.readBoolean();
        receiverListener.onSync(side, oldValue, value);
    }

    @Override
    public Data writeData() {
        return BooleanData.valueOf(value);
    }

    @Override
    public void readData(@NotNull Data data) {
        value = data.getBoolean();
    }
}
