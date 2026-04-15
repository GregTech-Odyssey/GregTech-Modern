package com.gto.registrate.builders;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import com.gto.registrate.AbstractRegistrate;
import com.gto.registrate.util.nullness.NonNullSupplier;
import com.gto.registrate.util.nullness.NonnullType;

public class NoConfigBuilder<R, T extends R, P> extends AbstractBuilder<R, T, P, NoConfigBuilder<R, T, P>> {

    private final NonNullSupplier<T> factory;

    public NoConfigBuilder(AbstractRegistrate<?> owner, P parent, String name, ResourceKey<Registry<R>> registryType, NonNullSupplier<T> factory) {
        super(owner, parent, name, registryType);
        this.factory = factory;
    }

    @Override
    protected @NonnullType T createEntry() {
        return factory.get();
    }
}
