package com.gto.registrate.builders;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import com.gto.registrate.AbstractRegistrate;
import com.gto.registrate.ClientEvent;
import com.gto.registrate.providers.DataGenContext;
import com.gto.registrate.providers.ProviderType;
import com.gto.registrate.providers.RegistrateItemModelProvider;
import com.gto.registrate.providers.RegistrateLangProvider;
import com.gto.registrate.providers.RegistrateRecipeProvider;
import com.gto.registrate.util.CreativeModeTabModifier;
import com.gto.registrate.util.entry.ItemEntry;
import com.gto.registrate.util.entry.RegistryEntry;
import com.gto.registrate.util.nullness.NonNullBiConsumer;
import com.gto.registrate.util.nullness.NonNullFunction;
import com.gto.registrate.util.nullness.NonNullSupplier;
import com.gto.registrate.util.nullness.NonNullUnaryOperator;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;

import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

/**
 * A builder for items, allows for customization of the {@link Item.Properties} and configuration of data associated
 * with items (models, recipes, etc.).
 *
 * @param <T>
 *            The type of item being built
 * @param <P>
 *            Parent object type
 */
public class ItemBuilder<T extends Item, P> extends AbstractBuilder<Item, T, P, ItemBuilder<T, P>> {

    /**
     * Create a new {@link ItemBuilder} and configure data. Used in lieu of adding side-effects to constructor, so that
     * alternate initialization strategies can be done in subclasses.
     * <p>
     * The item will be assigned the following data:
     * <ul>
     * <li>A simple generated model with one texture (via {@link #defaultModel()})</li>
     * <li>The default translation (via {@link #defaultLang()})</li>
     * </ul>
     *
     * @param <T>
     *                The type of the builder
     * @param <P>
     *                Parent object type
     * @param owner
     *                The owning {@link AbstractRegistrate} object
     * @param parent
     *                The parent object
     * @param name
     *                Name of the entry being built
     * @param factory
     *                Factory to create the item
     * @return A new {@link ItemBuilder} with reasonable default data generators.
     */
    public static <T extends Item, P> ItemBuilder<T, P> create(AbstractRegistrate<?> owner, P parent, String name, NonNullFunction<Item.Properties, T> factory) {
        return new ItemBuilder<>(owner, parent, name, factory)
                .defaultModel().defaultLang();
    }

    private final NonNullFunction<Item.Properties, T> factory;

    private NonNullSupplier<Item.Properties> initialProperties = Item.Properties::new;
    private NonNullFunction<Item.Properties, Item.Properties> propertiesCallback = NonNullUnaryOperator.identity();

    private @Nullable Reference2ReferenceOpenHashMap<ResourceKey<CreativeModeTab>, Consumer<CreativeModeTabModifier>> creativeModeTabs;

    protected ItemBuilder(AbstractRegistrate<?> owner, P parent, String name, NonNullFunction<Item.Properties, T> factory) {
        super(owner, parent, name, ForgeRegistries.Keys.ITEMS);
        this.factory = factory;
    }

    /**
     * Modify the properties of the item. Modifications are done lazily, but the passed function is composed with the
     * current one, and as such this method can be called multiple times to perform
     * different operations.
     * <p>
     * If a different properties instance is returned, it will replace the existing one entirely.
     *
     * @param func
     *             The action to perform on the properties
     * @return this {@link ItemBuilder}
     */
    public ItemBuilder<T, P> properties(NonNullUnaryOperator<Item.Properties> func) {
        propertiesCallback = propertiesCallback.andThen(func);
        return this;
    }

    /**
     * Replace the initial state of the item properties, without replacing or removing any modifications done via
     * {@link #properties(NonNullUnaryOperator)}.
     *
     * @param properties
     *                   A supplier to to create the initial properties
     * @return this {@link ItemBuilder}
     */
    public ItemBuilder<T, P> initialProperties(NonNullSupplier<Item.Properties> properties) {
        initialProperties = properties;
        return this;
    }

    /**
     * Sets a tab modifier for the given tab which can be used to define custom logic for how the item stack is created
     * and/or added to the tab.
     *
     * <p>
     * CreativeModeTab registration is delegated off until the item has been finalized and registered to the
     * {@link net.minecraft.core.registries.BuiltInRegistries#ITEM Item registry}.<br>
     * This means you can call this method as many times as you like during the build process with no added side
     * effects.
     * <p>
     * Calling this method with different {@link ResourceKey tab keys} will add the modifier to all the specified tabs.
     * <p>
     * Calling this method multiple times with the same {@link ResourceKey tab key} will replace any existing modifier
     * for that tab.
     *
     * @param tab      A {@link ResourceKey} representing the {@link CreativeModeTab} to use the modifier for
     * @param modifier A {@link Consumer consumer} accepting a {@link CreativeModeTabModifier} used to update the tab
     * @return This builder
     */
    @Deprecated
    public ItemBuilder<T, P> tab(ResourceKey<CreativeModeTab> tab, Consumer<CreativeModeTabModifier> modifier) {
        var m = creativeModeTabs;
        if (m == null) creativeModeTabs = m = new Reference2ReferenceOpenHashMap<>(2);
        m.put(tab, modifier);
        return this;
    }

    /**
     * Adds the item built from this builder into the given CreativeModeTab using the default ItemStack instance.
     * <p>
     * CreativeModeTab registration is delegated off until the item has been finalized and registered to the
     * {@link net.minecraft.core.registries.BuiltInRegistries#ITEM Item registry}.<br>
     * This means you can call this method as many times as you like during the build process with no added side
     * effects.
     * <p>
     * Calling this method with different {@link ResourceKey tab keys} will add the item to all the specified tabs.
     * <p>
     * Calling this method multiple times with the same {@link NonNullSupplier tab supplier} will have no effect.
     *
     * @param tab A {@link ResourceKey} representing the {@link CreativeModeTab} to add to
     * @return This builder
     */
    public ItemBuilder<T, P> tab(ResourceKey<CreativeModeTab> tab) {
        var m = creativeModeTabs;
        if (m == null) creativeModeTabs = m = new Reference2ReferenceOpenHashMap<>(2);
        m.put(tab, CreativeModeTabModifier.DEFAULT);
        return this;
    }

    /**
     * Removes the modifier from this builder from the given {@link CreativeModeTab}.
     *
     * @param tab A {@link ResourceKey} representing the {@link CreativeModeTab} to remove the modifier from
     * @return This builder
     */
    public ItemBuilder<T, P> removeTab(ResourceKey<CreativeModeTab> tab) {
        if (creativeModeTabs != null) creativeModeTabs.remove(tab);
        return this;
    }

    /**
     * Register a block color handler for this item. The {@link ItemColor} instance can be shared across many items.
     *
     * @param colorHandler
     *                     The color handler to register for this item
     * @return this {@link ItemBuilder}
     */
    public ItemBuilder<T, P> color(NonNullSupplier<Supplier<ItemColor>> colorHandler) {
        if (GTCEu.isClientSide()) {
            ClientEvent.registerItemColorHandlers(asSupplier(), colorHandler.get());
        }
        return this;
    }

    /**
     * Assign the default model to this item, which is simply a generated model with a single texture of the same name.
     *
     * @return this {@link ItemBuilder}
     */
    public ItemBuilder<T, P> defaultModel() {
        return model((ctx, prov) -> prov.generated(ctx::getEntry));
    }

    /**
     * Configure the model for this item.
     *
     * @param cons
     *             The callback which will be invoked during data creation
     * @return this {@link ItemBuilder}
     * @see #setData(ProviderType, NonNullBiConsumer)
     */
    public ItemBuilder<T, P> model(NonNullBiConsumer<DataGenContext<Item, T>, RegistrateItemModelProvider> cons) {
        return setData(ProviderType.ITEM_MODEL, cons);
    }

    /**
     * Assign the default translation, as specified by
     * {@link RegistrateLangProvider#getAutomaticName(NonNullSupplier, net.minecraft.resources.ResourceKey)}. This is
     * the default, so it is generally
     * not necessary to call, unless for undoing previous changes.
     *
     * @return this {@link ItemBuilder}
     */
    public ItemBuilder<T, P> defaultLang() {
        return lang(Item::getDescriptionId);
    }

    /**
     * Set the translation for this item.
     *
     * @param name
     *             A localized English name
     * @return this {@link ItemBuilder}
     */
    public ItemBuilder<T, P> lang(String name) {
        return lang(Item::getDescriptionId, name);
    }

    /**
     * Configure the recipe(s) for this item.
     *
     * @param cons
     *             The callback which will be invoked during data generation.
     * @return this {@link ItemBuilder}
     * @see #setData(ProviderType, NonNullBiConsumer)
     */
    public ItemBuilder<T, P> recipe(NonNullBiConsumer<DataGenContext<Item, T>, RegistrateRecipeProvider> cons) {
        return setData(ProviderType.RECIPE, cons);
    }

    /**
     * Assign {@link TagKey}{@code s} to this item. Multiple calls will add additional tags.
     *
     * @param tags
     *             The tag to assign
     * @return this {@link ItemBuilder}
     */
    @SafeVarargs
    public final ItemBuilder<T, P> tag(TagKey<Item>... tags) {
        return tag(ProviderType.ITEM_TAGS, tags);
    }

    @Override
    protected T createEntry() {
        Item.Properties properties = this.initialProperties.get();
        properties = propertiesCallback.apply(properties);
        return factory.apply(properties);
    }

    @Override
    protected RegistryEntry<T> createEntryWrapper(ResourceKey<T> key) {
        return new ItemEntry<>(key);
    }

    @Override
    public ItemEntry<T> register() {
        var entry = (ItemEntry<T>) super.register();
        var tabs = creativeModeTabs;
        if (tabs == null || tabs.isEmpty()) {
            var tab = owner.defaultCreativeModeTab;
            if (tab != null) owner.modifyCreativeModeTab(tab, m -> m.acceptEntry(entry));
        } else {
            var visibility = CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS;
            for (var it = tabs.reference2ReferenceEntrySet().fastIterator(); it.hasNext();) {
                var e = it.next();
                var key = e.getKey();
                var value = e.getValue();
                if (value == CreativeModeTabModifier.DEFAULT) {
                    var finalVisibility = visibility;
                    owner.modifyCreativeModeTab(key, m -> m.acceptEntry(entry, finalVisibility));
                    visibility = CreativeModeTab.TabVisibility.PARENT_TAB_ONLY;
                } else {
                    owner.modifyCreativeModeTab(key, value);
                }
            }
            creativeModeTabs = null;
        }
        return entry;
    }
}
