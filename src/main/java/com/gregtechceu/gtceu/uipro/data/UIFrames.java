package com.gregtechceu.gtceu.uipro.data;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Consumer;

/**
 * 初始数据分帧：每帧以长度开头，读取出错或读写不对称时只影响这一帧，后面的数据照常读取。
 */
public final class UIFrames {

    private static final int MAX_FRAME = (1 << 21) - 1;

    private UIFrames() {}

    public static void write(FriendlyByteBuf buf, Consumer<FriendlyByteBuf> writer) {
        int start = buf.writerIndex();
        buf.writeMedium(0);
        writer.accept(buf);
        int length = buf.writerIndex() - start - 3;
        if (length == 0) {
            buf.writerIndex(start);
            buf.writeByte(0);
            return;
        }
        if (length > MAX_FRAME) throw new IllegalStateException("UI initial data frame too large: " + length);
        buf.setByte(start, (length & 0x7F) | 0x80);
        buf.setByte(start + 1, ((length >>> 7) & 0x7F) | 0x80);
        buf.setByte(start + 2, length >>> 14);
    }

    public static void read(FriendlyByteBuf buf, Consumer<FriendlyByteBuf> reader, Object owner) {
        int length = buf.readVarInt();
        if (length < 0 || length > buf.readableBytes()) throw new IllegalStateException("Invalid UI frame length " + length + " in " + UIFaults.where(owner));
        if (length == 0) return;
        var frame = new FriendlyByteBuf(buf.readSlice(length));
        try {
            reader.accept(frame);
            if (frame.isReadable()) GTCEu.LOGGER.error("UI initial data of {} left {} unread bytes (write/read asymmetry)", UIFaults.where(owner), frame.readableBytes());
        } catch (RuntimeException e) {
            GTCEu.LOGGER.error("UI initial data of {} could not be read", UIFaults.where(owner), e);
        }
    }
}
