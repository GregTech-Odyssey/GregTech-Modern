package com.tterrag.registrate.util.entry;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import com.tterrag.registrate.util.nullness.NonnullType;
import lombok.Getter;

import javax.annotation.Nullable;

public class RegistryEntry<T> implements NonNullSupplier<T> {

    public static final RegistryEntry<?> EMPTY = new RegistryEntry<>(null);

    public static <T> RegistryEntry<T> empty() {
        @SuppressWarnings("unchecked")
        RegistryEntry<T> t = (RegistryEntry<T>) EMPTY;
        return t;
    }

    @Getter
    protected final ResourceKey<T> key;

    @Nullable
    protected T value;

    public RegistryEntry(ResourceKey<T> key) {
        this.key = key;
    }

    public ResourceLocation getId() {
        return key.location();
    }

    public String getNamespace() {
        return key.location().getNamespace();
    }

    public String getPath() {
        return key.location().getPath();
    }

    public boolean isPresent() {
        return value != null;
    }

    /**
     * Get the entry, throwing an exception if it is not present for any reason.
     * 
     * @return The (non-null) entry
     */
    @Override
    public @NonnullType T get() {
        return value;
    }

    /**
     * Get the entry without performing any checks.
     * 
     * @return The (nullable) entry
     */
    public @Nullable T getUnchecked() {
        return value;
    }

    public <R, E extends R> RegistryEntry<E> getSibling(AbstractRegistrate<?> owner, ResourceKey<? extends Registry<R>> registryType) {
        return this == EMPTY ? empty() : owner.get(getPath(), registryType);
    }

    public <R, E extends R> RegistryEntry<E> getSibling(AbstractRegistrate<?> owner, IForgeRegistry<R> registry) {
        return getSibling(owner, registry.getRegistryKey());
    }

    public <R> boolean is(R entry) {
        return value == entry;
    }

    @SuppressWarnings("unchecked")
    protected static <E extends RegistryEntry<?>> E cast(Class<? super E> clazz, RegistryEntry<?> entry) {
        try {
            return (E) entry;
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Could not convert RegistryEntry: expecting " + clazz + ", found " + entry.getClass());
        }
    }

    public void bound(T value) {
        if (this.value != null) throw new IllegalStateException("key: " + key + " value already bound");
        this.value = value;
    }
}
