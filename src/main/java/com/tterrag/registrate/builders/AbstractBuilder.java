package com.tterrag.registrate.builders;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.core.Registry;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;
import net.minecraftforge.common.util.NonNullFunction;

import com.tterrag.registrate.AbstractRegistrate;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateLangProvider;
import com.tterrag.registrate.providers.RegistrateTagsProvider;
import com.tterrag.registrate.util.entry.RegistryEntry;
import com.tterrag.registrate.util.nullness.NonNullBiFunction;
import com.tterrag.registrate.util.nullness.NonNullConsumer;
import com.tterrag.registrate.util.nullness.NonNullSupplier;
import com.tterrag.registrate.util.nullness.NonnullType;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class which most builders should extend, instead of implementing [@link {@link Builder} directly.
 * <p>
 * Provides the most basic functionality, and some utility methods that remove the need to pass the registry class.
 *
 * @param <R>
 *            Type of the registry for the current object. This is the concrete base class that all registry entries
 *            must extend, and the type used for the forge registry itself.
 * @param <T>
 *            Actual type of the object being built.
 * @param <P>
 *            Type of the parent object, this is returned from {@link #build()} and {@link #getParent()}.
 * @param <S>
 *            Self type
 * @see Builder
 */
public abstract class AbstractBuilder<R, T extends R, P, S extends AbstractBuilder<R, T, P, S>> implements Builder<R, T, P, S> {

    @Getter(onMethod_ = { @Override })
    protected final AbstractRegistrate<?> owner;
    @Getter(onMethod_ = { @Override })
    protected final P parent;
    @Getter(onMethod_ = { @Override })
    protected final String name;

    @Getter(onMethod_ = { @Override })
    protected final ResourceKey<Registry<R>> registryKey;

    @Nullable
    protected List<NonNullConsumer<? super T>> callbacks;

    @Nullable
    protected final Reference2ReferenceOpenHashMap<ProviderType<? extends RegistrateTagsProvider<?>>, Reference2BooleanOpenHashMap<TagKey<?>>> tagsByType;

    /** A supplier for the entry that will discard the reference to this builder after it is resolved */
    protected final ValueSupplier<T, R> safeSupplier;

    protected AbstractBuilder(AbstractRegistrate<?> owner, P parent, String name, ResourceKey<Registry<R>> registryKey) {
        this.owner = owner;
        this.parent = parent;
        this.name = name;
        this.registryKey = registryKey;
        this.tagsByType = GTCEu.isDataGen() ? new Reference2ReferenceOpenHashMap<>() : null;
        this.safeSupplier = new ValueSupplier<>(owner, name, registryKey);
    }

    /**
     * Create the built entry. This method will be lazily resolved at registration time, so it is safe to bake in values
     * from the builder.
     *
     * @return The built entry
     */
    @SuppressWarnings("null")
    protected abstract @NonnullType T createEntry();

    @Override
    public RegistryEntry<T> register() {
        if (tagsByType != null) {
            tagsByType.forEach(
                    (type, tags) -> setData(
                            type,
                            (ctx, prov) -> tags.forEach(
                                    (tag, isOptional) -> prov.addTag((TagKey) tag).add(asTag(isOptional)))));
        }
        var cbs = callbacks != null ? callbacks : Collections.<NonNullConsumer<? super T>>emptyList();
        return owner.register(name, registryKey, cbs, this::createEntry, this::createEntryWrapper);
    }

    protected RegistryEntry<T> createEntryWrapper(ResourceKey<T> key) {
        return new RegistryEntry<>(key);
    }

    @Override
    public NonNullSupplier<T> asSupplier() {
        return safeSupplier;
    }

    @Override
    public S onRegister(NonNullConsumer<? super T> callback) {
        if (callbacks == null) {
            callbacks = new ArrayList<>();
        }
        callbacks.add(callback);
        return (S) this;
    }

    /**
     * Tag this entry with a tag (or tags) of the correct type. Multiple calls will add additional tags.
     *
     * @param type
     *             The provider type (which must be a tag provider)
     * @param tags
     *             The tags to add
     * @return this {@link Builder}
     */
    public final <TP extends TagsProvider<R> & RegistrateTagsProvider<R>> S tag(ProviderType<? extends TP> type, TagKey<R>... tags) {
        return tag(type, false, tags);
    }

    /**
     * Tag this entry with a tag (or tags) of the correct type. Multiple calls will add additional tags.
     *
     * @param type
     *             The provider type (which must be a tag provider)
     * @param tags
     *             The tags to add
     * @return this {@link Builder}
     */
    @SuppressWarnings("unchecked")
    @SafeVarargs
    public final <TP extends TagsProvider<R> & RegistrateTagsProvider<R>> S tag(ProviderType<? extends TP> type, boolean isOptional, TagKey<R>... tags) {
        if (tagsByType != null) {
            var map = tagsByType.computeIfAbsent(type, k -> new Reference2BooleanOpenHashMap<>());
            for (var tag : tags) {
                map.put(tag, isOptional);
            }
        }
        return (S) this;
    }

    /**
     * Remove a tag (or tags) from this entry of a given type. Useful to remove default tags on fluids, for example.
     * Multiple calls will remove additional tags.
     *
     * @param type
     *             The provider type (which must be a tag provider)
     * @param tags
     *             The tags to remove
     * @return this {@link Builder}
     */
    @SuppressWarnings("unchecked")
    @SafeVarargs
    public final <TP extends TagsProvider<R> & RegistrateTagsProvider<R>> S removeTag(ProviderType<TP> type, TagKey<R>... tags) {
        if (tagsByType != null) {
            var set = tagsByType.get(type);
            if (set != null) {
                for (var tag : tags) {
                    set.removeBoolean(tag);
                }
            }
        }
        return (S) this;
    }

    protected TagEntry asTag(boolean isOptional) {
        ResourceLocation id = new ResourceLocation(owner.getModid(), name);
        if (isOptional) return TagEntry.optionalElement(id);
        return TagEntry.element(id);
    }

    /**
     * Set the lang key for this entry to the default value (specified by
     * {@link RegistrateLangProvider#getAutomaticName(NonNullSupplier, ResourceKey)}). Generally, specific helpers from
     * concrete
     * builders should be used instead.
     *
     * @param langKeyProvider
     *                        A function to get the translation key from the entry
     * @return this {@link Builder}
     */
    public S lang(NonNullFunction<T, String> langKeyProvider) {
        return lang(langKeyProvider, (p, t) -> p.<R>getAutomaticName(t, getRegistryKey()));
    }

    /**
     * Set the lang key for this entry to the specified name. Generally, specific helpers from concrete builders should
     * be used instead.
     *
     * @param langKeyProvider
     *                        A function to get the translation key from the entry
     * @param name
     *                        The name to use
     * @return this {@link Builder}
     */
    public S lang(NonNullFunction<T, String> langKeyProvider, String name) {
        return lang(langKeyProvider, (p, s) -> name);
    }

    private S lang(NonNullFunction<T, String> langKeyProvider, NonNullBiFunction<RegistrateLangProvider, NonNullSupplier<? extends T>, String> localizedNameProvider) {
        return setData(ProviderType.LANG, (ctx, prov) -> prov.add(langKeyProvider.apply(ctx.getEntry()), localizedNameProvider.apply(prov, ctx::getEntry)));
    }

    public static final class ValueSupplier<T, R> implements NonNullSupplier<T> {

        private final AbstractRegistrate core;
        private final String name;
        private final ResourceKey<? extends Registry<R>> registryKey;
        @Nullable
        private T value;

        private ValueSupplier(
                              AbstractRegistrate core, String name, ResourceKey<? extends Registry<R>> registryKey) {
            this.core = core;
            this.name = name;
            this.registryKey = registryKey;
        }

        @Override
        public T get() {
            var value = this.value;
            if (value == null) {
                var entry = core.get(name, registryKey);
                value = this.value = (T) entry.get();
            }
            return value;
        }
    }
}
