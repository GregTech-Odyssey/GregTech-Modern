package com.gregtechceu.gtceu.uipro.data;

import com.gregtechceu.gtceu.GTCEu;

import org.jetbrains.annotations.Nullable;

/**
 * 代替 {@code @OnlyIn(CLIENT)} 字段：字段两端都存在、服务端恒为空，两端代码可安全判空；取出的值只能在 {@code @OnlyIn(CLIENT)} 方法里使用。
 */
public final class ClientOnly<T> {

    @Nullable
    private Object value;

    private ClientOnly() {}

    public static <T> ClientOnly<T> empty() {
        return new ClientOnly<>();
    }

    public void set(@Nullable T value) {
        if (value != null && GTCEu.isDev() && !GTCEu.isClientThread()) {
            GTCEu.LOGGER.warn("ClientOnly set off the client thread: {}", value.getClass().getName(), new IllegalStateException());
        }
        this.value = value;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public T get() {
        return (T) value;
    }

    public boolean isPresent() {
        return value != null;
    }

    public void clear() {
        value = null;
    }
}
