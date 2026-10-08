package com.gregtechceu.gtceu.uipro.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.uipro.ElementState;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

import com.gto.datasynclib.datastream.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * 客户端到服务端的请求（对应 LDLib2 RPCEvent）：服务端依次检查界面有效、令牌桶与单项限流、有界解码、剩余字节、控件禁用、控件激活链、参数校验，全部通过才执行；
 * 被拒时回告客户端执行 {@link #onRejected}。{@link #send} 只在客户端发出，没有服务端的界面里在本端直接处理。
 */
public final class RPC<T> {

    public static final int DEFAULT_LIMIT = 8;
    private static final int REJECT = 0;

    private final UIChannel channel;
    private final int id;
    private final StreamCodec<? super FriendlyByteBuf, T> codec;
    private final BiConsumer<Player, T> handler;
    @Nullable
    private Predicate<T> validator;
    @Nullable
    private Runnable onRejected;
    private int limit = DEFAULT_LIMIT;
    private int cost = UIBudget.REQUEST_COST;
    private boolean allowWhenDisabled;
    private boolean allowWhenHidden;
    private long lastTick = Long.MIN_VALUE;
    private int countThisTick;
    private long lastRejectReplyTick = Long.MIN_VALUE;

    RPC(UIChannel channel, int id, StreamCodec<? super FriendlyByteBuf, T> codec, BiConsumer<Player, T> handler) {
        this.channel = channel;
        this.id = id;
        this.codec = codec;
        this.handler = handler;
    }

    public RPC<T> validate(Predicate<T> validator) {
        this.validator = validator;
        return this;
    }

    public RPC<T> limit(int perTick) {
        this.limit = Math.max(1, perTick);
        return this;
    }

    public RPC<T> cost(int cost) {
        this.cost = Math.max(1, cost);
        return this;
    }

    public RPC<T> allowWhenDisabled() {
        this.allowWhenDisabled = true;
        return this;
    }

    public RPC<T> allowWhenHidden() {
        this.allowWhenHidden = true;
        return this;
    }

    public RPC<T> onRejected(Runnable onRejected) {
        this.onRejected = onRejected;
        return this;
    }

    public boolean send(T value) {
        if (channel.isLocal()) {
            accept(channel.player(), value);
            return true;
        }
        var owner = channel.getOwner();
        if (!owner.isRemote() || owner.isClientSideWidget()) return false;
        channel.sendToServer(id, buf -> codec.encode(buf, value));
        return true;
    }

    boolean isClientSide() {
        return channel.isLocal() || channel.getOwner().isRemote();
    }

    void receive(FriendlyByteBuf buf) {
        var owner = channel.getOwner();
        var gui = owner.getGui();
        if (owner.isRemote() || gui == null || gui.entityPlayer == null) return;
        if (gui.holder != null && gui.holder.isInvalid()) {
            drop("holder invalid");
            return;
        }
        long tick = gui.entityPlayer.level().getGameTime();
        if (!consumeQuota(tick) || !UIBudget.of(gui).tryConsume(tick, cost)) {
            drop("rate limit");
            return;
        }
        T value;
        try {
            value = codec.decode(buf);
        } catch (RuntimeException e) {
            drop("malformed payload: " + e.getMessage());
            return;
        }
        if (buf.isReadable()) {
            drop(buf.readableBytes() + " trailing bytes");
            return;
        }
        accept(gui.entityPlayer, value);
    }

    void receiveReply(FriendlyByteBuf buf) {
        if (buf.readByte() == REJECT && onRejected != null) onRejected.run();
    }

    void reject() {
        if (onRejected == null) return;
        if (channel.isLocal()) {
            onRejected.run();
            return;
        }
        var owner = channel.getOwner();
        if (owner.isRemote()) return;
        var player = channel.player();
        long tick = player == null ? 0 : player.level().getGameTime();
        if (tick == lastRejectReplyTick) return;
        lastRejectReplyTick = tick;
        channel.sendToClient(id, buf -> buf.writeByte(REJECT));
    }

    private void accept(@Nullable Player player, T value) {
        var owner = channel.getOwner();
        if (!allowWhenDisabled && ElementState.isDisabled(owner)) {
            drop("disabled");
            return;
        }
        if (!allowWhenHidden && !isActiveChain(owner)) {
            drop("inactive");
            return;
        }
        if (validator != null && !validator.test(value)) {
            drop("invalid value");
            return;
        }
        handler.accept(player, value);
    }

    private boolean consumeQuota(long tick) {
        if (tick != lastTick) {
            lastTick = tick;
            countThisTick = 0;
        }
        return ++countThisTick <= limit;
    }

    private static boolean isActiveChain(Widget widget) {
        for (var current = widget; current != null; current = current.getParent()) {
            if (!current.isActive()) return false;
        }
        return true;
    }

    private void drop(String reason) {
        reject();
        var owner = channel.getOwner();
        if (GTCEu.isDev()) GTCEu.LOGGER.warn("Dropped UI request {} on {}: {}", Integer.toHexString(id), owner.getClass().getName(), reason);
        else GTCEu.LOGGER.debug("Dropped UI request {} on {}: {}", Integer.toHexString(id), owner.getClass().getName(), reason);
    }
}
