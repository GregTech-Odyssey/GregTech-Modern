package com.gregtechceu.gtceu.api.data.worldgen.bedrock;

import com.gregtechceu.gtceu.api.data.worldgen.BiomeWeightModifier;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.utils.RegistryUtil;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import com.gto.fastcollection.fastutil.OpenCacheHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Shared data of a bedrock vein type: selection weight, biome and dimension filters and depletion rules.
 */
public abstract class BedrockVeinDefinition {

    private final Component name;
    private final int weight;
    private final int depletionAmount;
    private final int depletionChance;
    private final int depletedYield;
    private final BiomeWeightModifier biomeWeightModifier;
    @Nullable
    private final Set<ResourceKey<Level>> dimensionFilter;

    protected BedrockVeinDefinition(Builder<?, ?> builder) {
        this.name = Component.translatable(builder.nameKey);
        this.weight = builder.weight;
        this.depletionAmount = builder.depletionAmount;
        this.depletionChance = builder.depletionChance;
        this.depletedYield = builder.depletedYield;
        this.biomeWeightModifier = combine(builder.biomes);
        this.dimensionFilter = builder.dimensions;
    }

    public Component getName() {
        return name;
    }

    public int weight() {
        return weight;
    }

    public int depletionAmount() {
        return depletionAmount;
    }

    public int depletionChance() {
        return depletionChance;
    }

    public int depletedYield() {
        return depletedYield;
    }

    public BiomeWeightModifier biomeWeightModifier() {
        return biomeWeightModifier;
    }

    @Nullable
    public Set<ResourceKey<Level>> dimensionFilter() {
        return dimensionFilter;
    }

    public int weightIn(Holder<Biome> biome) {
        return weight + biomeWeightModifier.applyAsInt(biome);
    }

    public boolean generatesIn(ResourceKey<Level> dimension) {
        return dimensionFilter == null || dimensionFilter.contains(dimension);
    }

    private static BiomeWeightModifier combine(List<BiomeWeightModifier> modifiers) {
        int added = 0;
        for (var modifier : modifiers) added += modifier.addedWeight;
        return new BiomeWeightModifier(() -> HolderSet.direct(modifiers.stream().flatMap(mod -> mod.biomes.get().stream()).toList()), added) {

            @Override
            public int applyAsInt(Holder<Biome> biome) {
                int mod = 0;
                for (var modifier : modifiers) {
                    mod += modifier.applyAsInt(biome);
                }
                return mod;
            }
        };
    }

    @SuppressWarnings("unchecked")
    public abstract static class Builder<B extends Builder<B, D>, D extends BedrockVeinDefinition> {

        protected final ResourceLocation name;
        private String nameKey;
        private int weight;
        private int depletionAmount;
        private int depletionChance = 1;
        private int depletedYield;
        @Nullable
        private Set<ResourceKey<Level>> dimensions;
        private final List<BiomeWeightModifier> biomes = new ObjectArrayList<>();

        protected Builder(ResourceLocation name, String nameKey) {
            this.name = name;
            this.nameKey = nameKey;
        }

        public B nameKey(String nameKey) {
            this.nameKey = nameKey;
            return (B) this;
        }

        public B weight(int weight) {
            this.weight = weight;
            return (B) this;
        }

        public B depletionAmount(int depletionAmount) {
            this.depletionAmount = depletionAmount;
            return (B) this;
        }

        public B depletionChance(int depletionChance) {
            this.depletionChance = depletionChance;
            return (B) this;
        }

        public B depletedYield(int depletedYield) {
            this.depletedYield = depletedYield;
            return (B) this;
        }

        public B biomes(int weight, TagKey<Biome> biomes) {
            this.biomes.add(new BiomeWeightModifier(() -> GTRegistries.builtinRegistry().registryOrThrow(Registries.BIOME).getOrCreateTag(biomes), weight));
            return (B) this;
        }

        @SafeVarargs
        public final B biomes(int weight, ResourceKey<Biome>... biomes) {
            this.biomes.add(new BiomeWeightModifier(() -> HolderSet.direct(GTRegistries.builtinRegistry().registryOrThrow(Registries.BIOME)::getHolderOrThrow, biomes), weight));
            return (B) this;
        }

        public B biomes(int weight, HolderSet<Biome> biomes) {
            this.biomes.add(new BiomeWeightModifier(() -> biomes, weight));
            return (B) this;
        }

        public B dimensions(Set<ResourceKey<Level>> dimensions) {
            this.dimensions = dimensions;
            return (B) this;
        }

        public B dimensions(String... dimensions) {
            return dimensions(new OpenCacheHashSet<>(RegistryUtil.resolveResourceKeys(Registries.DIMENSION, dimensions)));
        }

        public abstract D register();
    }
}
