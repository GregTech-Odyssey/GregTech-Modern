package com.gregtechceu.gtceu.utils;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.Map;
import java.util.function.Consumer;

public class Event<C> {

    Map<Class<?>, Consumer<C>> listeners;

    private Event() {
        this.listeners = new Reference2ReferenceOpenHashMap<>();
    }

    public static <C> Event<C> create() {
        return new Event<>();
    }

    public static <C> Event<C> createRegister() {
        return new RegisterEvent<>();
    }

    public void addListener(Class<?> clazz, Consumer<C> listener) {
        this.listeners.put(clazz, listener);
    }

    public void addListener(Class<?> clazz, Runnable listener) {
        addListener(clazz, context -> listener.run());
    }

    public void removeListener(Class<?> clazz) {
        this.listeners.remove(clazz);
    }

    public void call(Class<?> clazz, C context) {
        this.listeners.get(clazz).accept(context);
    }

    public void call(C context) {
        this.listeners.values().forEach(listener -> listener.accept(context));
    }

    public void call() {
        call(null);
    }

    private static class RegisterEvent<C> extends Event<C> {

        @Override
        public void call(C context) {
            super.call(context);
            listeners = null;
        }
    }
}
