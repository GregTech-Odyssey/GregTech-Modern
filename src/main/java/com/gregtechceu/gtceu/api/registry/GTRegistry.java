package com.gregtechceu.gtceu.api.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;

import com.fast.fastcollection.O2OOpenCacheHashMap;
import com.gto.datasynclib.datasream.codec.DataCodec;
import com.gto.datasynclib.util.DataCodecs;
import com.gto.datasynclib.util.Registry;
import com.mojang.serialization.Codec;
import lombok.Getter;

import java.util.Map;

public abstract class GTRegistry<K extends Comparable<K>, V> extends Registry<K, V> {

    public static final Map<ResourceLocation, GTRegistry<?, ?>> REGISTERED = new O2OOpenCacheHashMap<>();

    @Getter
    protected final ResourceLocation registryName;
    private final boolean checkContext;

    public GTRegistry(ResourceLocation registryName, boolean checkContext) {
        super(registryName.toString());
        this.registryName = registryName;
        this.checkContext = checkContext;
    }

    public GTRegistry(ResourceLocation registryName) {
        super(registryName.toString());
        this.registryName = registryName;
        this.checkContext = true;
    }

    @Override
    public boolean checkContext() {
        if (!checkContext) return true;
        ModContainer container = ModLoadingContext.get().getActiveContainer();
        return container != null && (container.getModId().equals(this.registryName.getNamespace()));
    }

    public abstract DataCodec<V> dataCodec();

    public abstract Codec<V> codec();

    // ************************ Built-in Registry ************************//
    public static class Str<V> extends GTRegistry<String, V> {

        private final DataCodec<V> dataCodec = super.dataCodec(DataCodec.STRING_CODEC);

        public Str(ResourceLocation registryName) {
            super(registryName);
        }

        @Override
        public DataCodec<V> dataCodec() {
            return dataCodec;
        }

        public Str(ResourceLocation registryName, boolean checkContext) {
            super(registryName, checkContext);
        }

        @Override
        public Codec<V> codec() {
            return super.codec(Codec.STRING);
        }
    }

    public static class RL<V> extends GTRegistry<ResourceLocation, V> {

        private final DataCodec<V> dataCodec = super.dataCodec(DataCodecs.RESOURCE_LOCATION_CODEC);

        public RL(ResourceLocation registryName) {
            super(registryName);
        }

        public RL(ResourceLocation registryName, boolean checkContext) {
            super(registryName, checkContext);
        }

        @Override
        public DataCodec<V> dataCodec() {
            return dataCodec;
        }

        @Override
        public Codec<V> codec() {
            return super.codec(ResourceLocation.CODEC);
        }
    }
}
