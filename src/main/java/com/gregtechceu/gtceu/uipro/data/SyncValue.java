package com.gregtechceu.gtceu.uipro.data;

import com.gregtechceu.gtceu.uipro.UIElement;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import com.gto.datasynclib.util.StreamCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 服务端到客户端（S2C）的单值绑定，概念上对应 LDLib2 的 {@code SyncValue} + {@code DataBindingBuilder.xxxS2C}。
 * <p>
 * 服务端：打开界面时随初始数据下发一次；之后每次 {@code detectAndSendChanges} 比较 getter，变了才下发。
 * 客户端：收到新值（含初始值）后回调 {@link #onChanged}。
 * <p>
 * 没有服务端的界面（根实现 {@link com.gregtechceu.gtceu.uipro.ILocalUI}，如 EMI 里的配方页）：getter 就是数据源，
 * 每帧直接用本端 getter 取值，变了照常回调 {@link #onChanged}。
 * <p>
 * 由 {@link UIChannel} 统一编号和收发，挂到 {@link UIElement} 上使用。
 */
public final class SyncValue<T> {

    private final Supplier<T> getter;
    private final ByteStreamCodec<T> codec;
    private T value;
    @Nullable
    private Consumer<T> onChanged;

    private SyncValue(Supplier<T> getter, ByteStreamCodec<T> codec, T initialValue) {
        this.getter = getter;
        this.codec = codec;
        this.value = initialValue;
    }

    public static <T> SyncValue<T> of(Supplier<T> getter, ByteStreamCodec<T> codec, T initialValue) {
        return new SyncValue<>(getter, codec, initialValue);
    }

    public static SyncValue<Integer> ofInt(Supplier<Integer> getter, int initialValue) {
        return of(getter, ByteStreamCodec.INT_CODEC, initialValue);
    }

    public static SyncValue<Long> ofLong(Supplier<Long> getter, long initialValue) {
        return of(getter, ByteStreamCodec.LONG_CODEC, initialValue);
    }

    public static SyncValue<Boolean> ofBool(BooleanSupplier getter, boolean initialValue) {
        return of(getter::getAsBoolean, ByteStreamCodec.BOOLEAN_CODEC, initialValue);
    }

    public static SyncValue<Boolean> ofBool(BooleanSupplier getter) {
        return ofBool(getter, false);
    }

    public static SyncValue<Component> ofComponent(Supplier<Component> getter) {
        return of(getter, StreamCodecs.COMPONENT_CODEC, getter.get());
    }

    public static SyncValue<Component> ofComponent(Supplier<Component> getter, Component initialValue) {
        return of(getter, StreamCodecs.COMPONENT_CODEC, initialValue);
    }

    public static SyncValue<SyncItem> ofItem(Supplier<ItemStack> getter) {
        return ofItem(getter, ItemStack.EMPTY);
    }

    public static SyncValue<SyncItem> ofItem(Supplier<ItemStack> getter, ItemStack initialValue) {
        var last = new SyncItem[] { SyncItem.of(initialValue) };
        return of(() -> {
            var stack = getter.get();
            if (!last[0].matches(stack)) last[0] = SyncItem.of(stack);
            return last[0];
        }, SyncItem.CODEC, last[0]);
    }

    /** 客户端收到不同的新值时回调（服务端比较出变化时也会回调）。 */
    public SyncValue<T> onChanged(Consumer<T> listener) {
        this.onChanged = listener;
        return this;
    }

    /** 最近一次同步到的值；客户端渲染一律读它。 */
    public T getValue() {
        return value;
    }

    void prime() {
        accept(getter.get());
    }

    void writeInitial(FriendlyByteBuf buf) {
        value = getter.get();
        codec.encode(buf, value);
    }

    void readInitial(FriendlyByteBuf buf) {
        accept(codec.decode(buf));
    }

    boolean detectChange() {
        var latest = getter.get();
        if (Objects.equals(latest, value)) return false;
        accept(latest);
        return true;
    }

    void write(FriendlyByteBuf buf) {
        codec.encode(buf, value);
    }

    void read(FriendlyByteBuf buf) {
        accept(codec.decode(buf));
    }

    void pollLocal() {
        accept(getter.get());
    }

    private void accept(T newValue) {
        var old = value;
        value = newValue;
        if (onChanged != null && !Objects.equals(old, newValue)) onChanged.accept(newValue);
    }
}
