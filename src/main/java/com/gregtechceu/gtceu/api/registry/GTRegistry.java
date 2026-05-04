package com.gregtechceu.gtceu.api.registry;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModLoadingContext;

import com.fast.fastcollection.O2OOpenCacheHashMap;
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
        ModContainer container = ModLoadingContext.get().getActiveContainer();
        return container != null && (container.getModId().equals(this.registryName.getNamespace()) || container.getModId().equals(GTCEu.MOD_ID) || container.getModId().equals("minecraft"));
    }

    public abstract Codec<V> codec();

    // ************************ Built-in Registry ************************//
    public static class String<V> extends GTRegistry<java.lang.String, V> {

        public String(ResourceLocation registryName) {
            super(registryName);
        }

        public String(ResourceLocation registryName, boolean checkContext) {
            super(registryName, checkContext);
        }

        @Override
        public Codec<V> codec() {
            return super.codec(Codec.STRING);
        }
    }

    public static class RL<V> extends GTRegistry<ResourceLocation, V> {

        public RL(ResourceLocation registryName) {
            super(registryName);
        }

        public RL(ResourceLocation registryName, boolean checkContext) {
            super(registryName, checkContext);
        }

        @Override
        public Codec<V> codec() {
            return super.codec(ResourceLocation.CODEC);
        }
    }
}
