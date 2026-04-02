package com.tterrag.registrate.util;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.util.nullness.NonnullType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.annotation.Nullable;

@RequiredArgsConstructor
@Log4j2
public class OneTimeEventReceiver<T extends Event> implements Consumer<@NonnullType T> {

    public static <T extends Event & IModBusEvent> void addModListener(AbstractRegistrate<?> owner, Class<? super T> evtClass, Consumer<? super T> listener) {
        OneTimeEventReceiver.<T>addModListener(owner, EventPriority.NORMAL, evtClass, listener);
    }

    public static <T extends Event & IModBusEvent> void addModListener(AbstractRegistrate<?> owner, EventPriority priority, Class<? super T> evtClass, Consumer<? super T> listener) {
        OneTimeEventReceiver.<T>addListener(owner.getModEventBus(), priority, evtClass, listener);
    }

    public static <T extends Event> void addForgeListener(Class<? super T> evtClass, Consumer<? super T> listener) {
        OneTimeEventReceiver.<T>addForgeListener(EventPriority.NORMAL, evtClass, listener);
    }

    public static <T extends Event> void addForgeListener(EventPriority priority, Class<? super T> evtClass, Consumer<? super T> listener) {
        OneTimeEventReceiver.<T>addListener(MinecraftForge.EVENT_BUS, priority, evtClass, listener);
    }

    @Deprecated
    public static <T extends Event> void addListener(IEventBus bus, Class<? super T> evtClass, Consumer<? super T> listener) {
        OneTimeEventReceiver.<T>addListener(bus, EventPriority.NORMAL, evtClass, listener);
    }

    @SuppressWarnings("unchecked")
    @Deprecated
    public static <T extends Event> void addListener(IEventBus bus, EventPriority priority, Class<? super T> evtClass, Consumer<? super T> listener) {
        bus.addListener(priority, false, (Class<T>) evtClass, new OneTimeEventReceiver<>(bus, listener));
    }

    private static final @Nullable MethodHandle getBusId;
    static {
        MethodHandle ret;
        try {
            ret = MethodHandles.lookup().unreflectGetter(ObfuscationReflectionHelper.findField(EventBus.class, "busID"));
        } catch (IllegalAccessException e) {
            log.warn("Failed to set up EventBus reflection to release one-time event listeners, leaks will occur. This is not a big deal.");
            ret = null;
        }
        getBusId = ret;
    }

    private final IEventBus bus;
    private final Consumer<? super T> listener;
    private final AtomicBoolean consumed = new AtomicBoolean();

    @Override
    public void accept(T event) {
        if (consumed.compareAndSet(false, true)) {
            listener.accept(event);
        }
    }

    public static synchronized void unregister(AbstractRegistrate<?> owner, Object listener, Class<? extends Event> event) {}
}
