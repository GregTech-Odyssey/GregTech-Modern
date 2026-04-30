package com.gto.datasynclib.listener;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.CombinationCodec;
import com.gto.datasynclib.LogicalSide;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Setter
public final class ObjNotifiableHolder<T> extends ObjSerializableHolder<T> implements ISyncNotifiable<ObjSyncListener<T>> {

    public static ObjNotifiableHolder<String> createString() {
        return new ObjNotifiableHolder<>(CombinationCodec.STRING_CODEC);
    }

    public static ObjNotifiableHolder<UUID> createUUID() {
        return new ObjNotifiableHolder<>(CombinationCodec.UUID_CODEC);
    }

    public static <T> ObjNotifiableHolder<T> create(CombinationCodec<T> codec) {
        return new ObjNotifiableHolder<>(codec);
    }

    private ObjSyncListener<T> receiverListener = ObjSyncListener.EMPTY;
    private ObjSyncListener<T> senderListener = ObjSyncListener.EMPTY;

    private ObjNotifiableHolder(CombinationCodec<T> codec) {
        super(codec);
    }

    @Override
    public void writeBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        if (value == null) {
            data.writeBoolean(false);
        } else {
            data.writeBoolean(true);
            codec.streamWriter.encode(value, data);
        }
        senderListener.onSync(side, lastValue, value);
        lastValue = value;
    }

    @Override
    public void readBuf(LogicalSide side, @NotNull FriendlyByteBuf data) {
        var oldValue = value;
        if (data.readBoolean()) {
            value = codec.streamReader.decode(data);
        } else {
            value = null;
        }
        receiverListener.onSync(side, oldValue, value);
    }
}
