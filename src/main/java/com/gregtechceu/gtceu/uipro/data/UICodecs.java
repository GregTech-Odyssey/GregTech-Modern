package com.gregtechceu.gtceu.uipro.data;

import com.lowdragmc.lowdraglib.gui.util.ClickData;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Unit;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import com.gto.datasynclib.datastream.codec.StreamCodec;
import io.netty.handler.codec.DecoderException;

import java.util.ArrayList;
import java.util.List;

/**
 * DataSyncLib 没有的界面编解码器；基础类型与组合器直接用 {@link ByteStreamCodec} / {@code StreamCodecs}。
 * 客户端上行的集合必须用 {@link #list}：DataSyncLib 的 {@code collection} 按上报数量预分配，伪造的数量会耗尽内存。
 */
public final class UICodecs {

    public static final StreamCodec<FriendlyByteBuf, Unit> UNIT = StreamCodec.unit(Unit.INSTANCE);
    public static final StreamCodec<FriendlyByteBuf, Wheel> WHEEL = StreamCodec.composite(
            ByteStreamCodec.BOOLEAN_CODEC, Wheel::up,
            ByteStreamCodec.BOOLEAN_CODEC, Wheel::shift,
            ByteStreamCodec.BOOLEAN_CODEC, Wheel::ctrl,
            Wheel::new);
    public static final StreamCodec<FriendlyByteBuf, ClickData> CLICK = StreamCodec.of((buf, value) -> {
        buf.writeVarInt(value.button);
        buf.writeBoolean(value.isShiftClick);
        buf.writeBoolean(value.isCtrlClick);
    }, ClickData::readFromBuf);

    private UICodecs() {}

    public static <T> StreamCodec<FriendlyByteBuf, List<T>> list(StreamCodec<? super FriendlyByteBuf, T> element, int maxSize) {
        return ByteStreamCodec.collection(size -> {
            if (size < 0 || size > maxSize) throw new DecoderException("Invalid list size " + size);
            return new ArrayList<>(size);
        }, element);
    }

    public static StreamCodec<FriendlyByteBuf, float[]> floats(int maxLength) {
        return StreamCodec.of((buf, value) -> {
            buf.writeVarInt(value.length);
            for (float v : value) buf.writeFloat(v);
        }, buf -> {
            int size = buf.readVarInt();
            if (size < 0 || size > maxLength) throw new DecoderException("Invalid array size " + size);
            var value = new float[size];
            for (int i = 0; i < size; i++) value[i] = buf.readFloat();
            return value;
        });
    }

    public static StreamCodec<FriendlyByteBuf, String> utf(int maxLength) {
        return StreamCodec.of((buf, value) -> buf.writeUtf(value, maxLength), buf -> buf.readUtf(maxLength));
    }

    public static <E extends Enum<E>> StreamCodec<FriendlyByteBuf, E> enumOf(Class<E> type) {
        var constants = type.getEnumConstants();
        return StreamCodec.of((buf, value) -> buf.writeVarInt(value.ordinal()), buf -> {
            int ordinal = buf.readVarInt();
            if (ordinal < 0 || ordinal >= constants.length) throw new DecoderException("Invalid enum ordinal " + ordinal);
            return constants[ordinal];
        });
    }

    public record Wheel(boolean up, boolean shift, boolean ctrl) {}
}
