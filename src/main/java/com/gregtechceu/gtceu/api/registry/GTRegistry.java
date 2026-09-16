package com.gregtechceu.gtceu.api.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;

import com.gto.datasynclib.datastream.codec.DataCodec;
import com.gto.datasynclib.util.DataCodecs;
import com.gto.datasynclib.util.Registry;
import com.gto.fastcollection.fastutil.O2OOpenCacheHashMap;
import com.mojang.serialization.Codec;
import lombok.Getter;

import java.util.function.Function;

public abstract class GTRegistry<K extends Comparable<K>, V> extends Registry<K, V> {

    public static final O2OOpenCacheHashMap<ResourceLocation, GTRegistry<?, ?>> REGISTERED = new O2OOpenCacheHashMap<>();

    @Getter
    protected final ResourceLocation registryName;
    private final boolean checkContext;

    public GTRegistry(ResourceLocation registryName, DataCodec<K> keyCodec, Function<? super V, ? extends K> keyGetter, boolean checkContext) {
        super(registryName.toString(), keyCodec, keyGetter);
        this.registryName = registryName;
        this.checkContext = checkContext;
    }

    public GTRegistry(ResourceLocation registryName, DataCodec<K> keyCodec, boolean checkContext) {
        super(registryName.toString(), keyCodec, null);
        this.registryName = registryName;
        this.checkContext = checkContext;
    }

    public GTRegistry(ResourceLocation registryName, DataCodec<K> keyCodec, Function<? super V, ? extends K> keyGetter) {
        this(registryName, keyCodec, keyGetter, true);
    }

    @Override
    public boolean isContextValid() {
        if (!checkContext) return true;
        ModContainer container = ModLoadingContext.get().getActiveContainer();
        return container != null && (container.getModId().equals(this.registryName.getNamespace()));
    }

    public abstract Codec<V> codec();

    // ************************ Built-in Registry ************************//
    public static class Str<V> extends GTRegistry<String, V> {

        public Str(ResourceLocation registryName, Function<? super V, String> keyGetter) {
            this(registryName, keyGetter, true);
        }

        public Str(ResourceLocation registryName, Function<? super V, String> keyGetter, boolean checkContext) {
            super(registryName, DataCodec.STRING_CODEC, keyGetter, checkContext);
        }

        public Str(ResourceLocation registryName) {
            this(registryName, true);
        }

        public Str(ResourceLocation registryName, boolean checkContext) {
            super(registryName, DataCodec.STRING_CODEC, checkContext);
        }

        @Override
        public Codec<V> codec() {
            return super.codec(Codec.STRING);
        }
    }

    public static class RL<V> extends GTRegistry<ResourceLocation, V> {

        public RL(ResourceLocation registryName, Function<? super V, ResourceLocation> keyGetter) {
            this(registryName, keyGetter, true);
        }

        public RL(ResourceLocation registryName, Function<? super V, ResourceLocation> keyGetter, boolean checkContext) {
            super(registryName, DataCodecs.RESOURCE_LOCATION_CODEC, keyGetter, checkContext);
        }

        public RL(ResourceLocation registryName) {
            this(registryName, true);
        }

        public RL(ResourceLocation registryName, boolean checkContext) {
            super(registryName, DataCodecs.RESOURCE_LOCATION_CODEC, checkContext);
        }

        @Override
        public Codec<V> codec() {
            return super.codec(ResourceLocation.CODEC);
        }
    }
}
