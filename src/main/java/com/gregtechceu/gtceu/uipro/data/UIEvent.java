package com.gregtechceu.gtceu.uipro.data;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datastream.codec.StreamCodec;

import java.util.function.Consumer;

/**
 * 服务端到客户端的单次推送（增量、提示等不适合做成状态值的数据）：服务端 {@link #send}，客户端收到后执行处理器；初始数据之前发出的推送丢弃。
 */
public final class UIEvent<T> {

    private final UIChannel channel;
    private final int id;
    private final StreamCodec<? super FriendlyByteBuf, T> codec;
    private final Consumer<T> handler;

    UIEvent(UIChannel channel, int id, StreamCodec<? super FriendlyByteBuf, T> codec, Consumer<T> handler) {
        this.channel = channel;
        this.id = id;
        this.codec = codec;
        this.handler = handler;
    }

    public void send(T value) {
        if (channel.isLocal()) {
            handler.accept(value);
            return;
        }
        channel.sendToClient(id, buf -> codec.encode(buf, value));
    }

    void receive(FriendlyByteBuf buf) {
        handler.accept(codec.decode(buf));
    }
}
