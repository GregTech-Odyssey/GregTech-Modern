package com.gregtechceu.gtceu.uipro.data;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 双向绑定（{@link SyncValue} 下发 + {@link RPC} 上行）：客户端 {@link #set} 立即显示新值并请求服务端，
 * 服务端校验后写入，以下发的值为准；被拒或服务端值未变时回滚到已同步的值。
 */
public final class Binding<T> {

    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private final ByteStreamCodec<T> codec;
    private final T initialValue;
    @Nullable
    private Predicate<T> validator;
    @Nullable
    private Runnable onRejected;
    @Nullable
    private Consumer<T> onChanged;
    private boolean allowWhenHidden;
    @Nullable
    private SyncValue<T> value;
    @Nullable
    private RPC<T> request;
    @Nullable
    private T pending;
    private boolean hasPending;

    private Binding(Supplier<T> getter, Consumer<T> setter, ByteStreamCodec<T> codec, T initialValue) {
        this.getter = getter;
        this.setter = setter;
        this.codec = codec;
        this.initialValue = initialValue;
    }

    public static <T> Binding<T> bind(Supplier<T> getter, Consumer<T> setter, ByteStreamCodec<T> codec, T initialValue) {
        return new Binding<>(getter, setter, codec, initialValue);
    }

    public static Binding<Boolean> bindBool(BooleanSupplier getter, BooleanConsumer setter) {
        return new Binding<>(getter::getAsBoolean, setter::accept, ByteStreamCodec.BOOLEAN_CODEC, false);
    }

    public static Binding<Integer> bindInt(IntSupplier getter, IntConsumer setter, int min, int max) {
        return new Binding<Integer>(getter::getAsInt, setter::accept, ByteStreamCodec.INT_CODEC, min)
                .validate(value -> value >= min && value <= max);
    }

    public static Binding<Long> bindLong(LongSupplier getter, LongConsumer setter) {
        return new Binding<>(getter::getAsLong, setter::accept, ByteStreamCodec.LONG_CODEC, 0L);
    }

    public static Binding<String> bindString(Supplier<String> getter, Consumer<String> setter, int maxLength) {
        return new Binding<>(getter, setter, UICodecs.utf(maxLength), "");
    }

    public static <E extends Enum<E>> Binding<E> bindEnum(Class<E> type, Supplier<E> getter, Consumer<E> setter) {
        var constants = type.getEnumConstants();
        return new Binding<>(getter, setter, UICodecs.enumOf(type), constants[0]);
    }

    public Binding<T> validate(Predicate<T> validator) {
        this.validator = validator;
        return this;
    }

    public Binding<T> onRejected(Runnable onRejected) {
        this.onRejected = onRejected;
        return this;
    }

    public Binding<T> onChanged(Consumer<T> onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    public Binding<T> allowWhenHidden() {
        this.allowWhenHidden = true;
        if (request != null) request.allowWhenHidden();
        return this;
    }

    void register(UIChannel channel) {
        if (value != null) throw new IllegalStateException("Binding registered twice");
        value = channel.addSyncValue(SyncValue.of(getter, codec, initialValue).onChanged(this::synced));
        request = channel.addRPC(codec, (player, next) -> serverSet(next)).onRejected(this::rejected);
        if (allowWhenHidden) request.allowWhenHidden();
    }

    public T getValue() {
        if (hasPending) return pending;
        return value == null ? getter.get() : value.getValue();
    }

    public boolean isPending() {
        return hasPending;
    }

    public void set(T next) {
        if (request == null || Objects.equals(next, getValue())) return;
        if (validator != null && !validator.test(next)) return;
        if (!request.isClientSide()) {
            setter.accept(next);
            return;
        }
        pending = next;
        hasPending = true;
        if (onChanged != null) onChanged.accept(next);
        request.send(next);
    }

    private void serverSet(T next) {
        if (validator != null && !validator.test(next)) {
            request.reject();
            return;
        }
        var before = getter.get();
        setter.accept(next);
        if (Objects.equals(before, getter.get())) request.reject();
    }

    private void synced(T synced) {
        pending = null;
        hasPending = false;
        if (onChanged != null) onChanged.accept(synced);
    }

    private void rejected() {
        if (!hasPending) return;
        pending = null;
        hasPending = false;
        if (onChanged != null && value != null) onChanged.accept(value.getValue());
        if (onRejected != null) onRejected.run();
    }
}
