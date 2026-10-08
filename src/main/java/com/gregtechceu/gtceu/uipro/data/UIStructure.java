package com.gregtechceu.gtceu.uipro.data;

import net.minecraft.network.FriendlyByteBuf;

import com.gto.datasynclib.datastream.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 服务端权威的结构状态（增删子控件）：客户端只发请求，服务端校验、准备后先下发再本端执行，两端同序执行同一个 apply；
 * 当前状态写在初始数据里、排在子控件之前，每次执行推进通道纪元，旧结构上发出的子控件请求被丢弃。
 */
public final class UIStructure<K> {

    private static final int REJECT = 0;
    private static final int APPLY = 1;
    private static final Object NO_PREPARE = new Object();

    private final UIChannel channel;
    private final int id;
    private final StreamCodec<? super FriendlyByteBuf, K> codec;
    private final Supplier<K> current;
    private final RPC<K> request;
    @Nullable
    private Function<K, ?> preparer;
    private BiConsumer<K, Object> applier = (key, prepared) -> {};
    @Nullable
    private Predicate<K> validator;
    @Nullable
    private Runnable onRejected;
    @Nullable
    private K pending;
    private boolean hasPending;
    private boolean serverOnly;

    UIStructure(UIChannel channel, int id, StreamCodec<? super FriendlyByteBuf, K> codec, Supplier<K> current) {
        this.channel = channel;
        this.id = id;
        this.codec = codec;
        this.current = current;
        this.request = new RPC<>(channel, id, codec, (player, value) -> serverRequest(value, true))
                .cost(UIBudget.STRUCTURE_COST).limit(4).onRejected(this::rejected);
    }

    public UIStructure<K> apply(Consumer<K> apply) {
        this.preparer = null;
        this.applier = (key, prepared) -> apply.accept(key);
        return this;
    }

    public <P> Prepared<P> prepare(Function<K, P> prepare) {
        return new Prepared<>(prepare);
    }

    public UIStructure<K> validate(Predicate<K> validator) {
        this.validator = validator;
        return this;
    }

    public UIStructure<K> onRejected(Runnable onRejected) {
        this.onRejected = onRejected;
        return this;
    }

    public UIStructure<K> limit(int perTick) {
        request.limit(perTick);
        return this;
    }

    public UIStructure<K> allowWhenDisabled() {
        request.allowWhenDisabled();
        return this;
    }

    public UIStructure<K> allowWhenHidden() {
        request.allowWhenHidden();
        return this;
    }

    public UIStructure<K> serverOnly() {
        this.serverOnly = true;
        return this;
    }

    public K getTarget() {
        return hasPending ? pending : current.get();
    }

    public boolean isPending() {
        return hasPending;
    }

    public void request(K value) {
        if (channel.isLocal()) {
            if (Objects.equals(value, current.get()) || !isValid(value)) return;
            var prepared = prepareFor(value);
            if (prepared == null) return;
            channel.bumpEpoch();
            applier.accept(value, prepared);
        } else if (channel.getOwner().isRemote()) {
            if (Objects.equals(value, getTarget())) return;
            pending = value;
            hasPending = true;
            request.send(value);
        } else {
            serverRequest(value, false);
        }
    }

    private void serverRequest(K value, boolean fromClient) {
        if (fromClient && serverOnly) {
            request.reject();
            return;
        }
        Object prepared = Objects.equals(value, current.get()) || !isValid(value) ? null : prepareFor(value);
        if (prepared == null) {
            if (fromClient) request.reject();
            return;
        }
        int epoch = channel.bumpEpoch();
        channel.sendToClient(id, buf -> {
            buf.writeByte(APPLY);
            buf.writeVarInt(epoch);
            codec.encode(buf, value);
        });
        applier.accept(value, prepared);
    }

    private boolean isValid(K value) {
        return validator == null || validator.test(value);
    }

    @Nullable
    private Object prepareFor(K value) {
        return preparer == null ? NO_PREPARE : preparer.apply(value);
    }

    private void rejected() {
        pending = null;
        hasPending = false;
        if (onRejected != null) onRejected.run();
    }

    RPC<K> request() {
        return request;
    }

    void writeState(FriendlyByteBuf buf) {
        codec.encode(buf, current.get());
    }

    void readState(FriendlyByteBuf buf) {
        var value = codec.decode(buf);
        if (!Objects.equals(value, current.get())) applier.accept(value, prepareFor(value));
    }

    void receive(FriendlyByteBuf buf) {
        if (buf.readByte() == REJECT) {
            rejected();
            return;
        }
        int epoch = buf.readVarInt();
        var value = codec.decode(buf);
        if (hasPending && Objects.equals(value, pending)) {
            pending = null;
            hasPending = false;
        }
        channel.setEpoch(epoch);
        applier.accept(value, prepareFor(value));
    }

    public final class Prepared<P> {

        private final Function<K, P> prepare;

        private Prepared(Function<K, P> prepare) {
            this.prepare = prepare;
        }

        @SuppressWarnings("unchecked")
        public UIStructure<K> apply(BiConsumer<K, P> apply) {
            preparer = prepare;
            applier = (key, prepared) -> apply.accept(key, prepared == NO_PREPARE ? null : (P) prepared);
            return UIStructure.this;
        }
    }
}
