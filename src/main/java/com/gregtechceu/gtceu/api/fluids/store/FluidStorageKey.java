package com.gregtechceu.gtceu.api.fluids.store;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconType;
import com.gregtechceu.gtceu.api.fluids.FluidState;
import com.gregtechceu.gtceu.utils.collection.O2OOpenCacheHashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

public final class FluidStorageKey {

    private static final Map<ResourceLocation, FluidStorageKey> keys = new O2OOpenCacheHashMap<>();
    private final ResourceLocation resourceLocation;
    private final TagKey<Fluid> extraTag;
    private final MaterialIconType iconType;
    private final Function<Material, String> registryNameFunction;
    private final Function<Material, String> translationKeyFunction;
    private final FluidState defaultFluidState;
    private final int registrationPriority;

    FluidStorageKey(@NotNull ResourceLocation resourceLocation, @Nullable TagKey<Fluid> extraTag, @NotNull MaterialIconType iconType, @NotNull Function<@NotNull Material, @NotNull String> registryNameFunction, @NotNull Function<@NotNull Material, @NotNull String> translationKeyFunction, @Nullable FluidState defaultFluidState, int registrationPriority) {
        this.resourceLocation = resourceLocation;
        this.extraTag = extraTag;
        this.iconType = iconType;
        this.registryNameFunction = registryNameFunction;
        this.translationKeyFunction = translationKeyFunction;
        this.defaultFluidState = defaultFluidState;
        this.registrationPriority = registrationPriority;
        if (keys.containsKey(resourceLocation)) {
            throw new IllegalArgumentException("Cannot create duplicate keys");
        }
        keys.put(resourceLocation, this);
    }

    public FluidStorageKey(@NotNull ResourceLocation resourceLocation, @NotNull MaterialIconType iconType, @NotNull Function<@NotNull Material, @NotNull String> registryNameFunction, @NotNull Function<@NotNull Material, @NotNull String> translationKeyFunction, @Nullable FluidState defaultFluidState, int registrationPriority) {
        this(resourceLocation, null, iconType, registryNameFunction, translationKeyFunction, defaultFluidState, registrationPriority);
    }

    @Nullable
    public static FluidStorageKey getByName(@NotNull ResourceLocation location) {
        return keys.get(location);
    }

    public static Collection<FluidStorageKey> allKeys() {
        return keys.values();
    }

    /**
     * @param baseName the base name of the fluid
     * @return the registry name to use
     */
    @NotNull
    public String getRegistryNameFor(@NotNull Material baseName) {
        return registryNameFunction.apply(baseName);
    }

    /**
     * @return the translation key for fluids with this key
     */
    @NotNull
    public String getTranslationKeyFor(@NotNull Material material) {
        return this.translationKeyFunction.apply(material);
    }

    @Override
    @NotNull
    public String toString() {
        return "FluidStorageKey{" + resourceLocation + '}';
    }

    public ResourceLocation getResourceLocation() {
        return this.resourceLocation;
    }

    public TagKey<Fluid> getExtraTag() {
        return this.extraTag;
    }

    public MaterialIconType getIconType() {
        return this.iconType;
    }

    public FluidState getDefaultFluidState() {
        return this.defaultFluidState;
    }

    public int getRegistrationPriority() {
        return this.registrationPriority;
    }
}
