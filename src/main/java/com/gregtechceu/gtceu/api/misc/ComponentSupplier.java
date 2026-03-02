package com.gregtechceu.gtceu.api.misc;

import net.minecraft.network.chat.Component;

import lombok.Getter;
import lombok.Setter;

import java.util.function.Supplier;

public class ComponentSupplier implements Supplier<Component> {

    @Setter
    @Getter
    protected String key;

    public ComponentSupplier(String key) {
        this.key = key;
    }

    @Override
    public Component get() {
        return Component.translatable(key);
    }

    public Component get(Object... args) {
        return Component.translatable(key, args);
    }
}
